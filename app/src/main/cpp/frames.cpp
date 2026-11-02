// Camera frames in YUV_420_888: luma statistics read straight from the Y plane, and a single pass that crops,
// turns upright and converts to RGBA only the region the landmark tracker will see.
#include <jni.h>
#include <android/bitmap.h>
#include <cmath>
#include <cstdint>
#include <cstdlib>

namespace {

inline uint8_t clamp8(int v) { return static_cast<uint8_t>(v < 0 ? 0 : (v > 255 ? 255 : v)); }

// Upright (ux, uy) to sensor (sx, sy) for a frame of sensor size w x h that needs `rot` degrees clockwise.
inline void toSensor(int rot, int w, int h, int ux, int uy, int &sx, int &sy) {
    switch (rot) {
        case 90: sx = uy; sy = h - 1 - ux; break;
        case 180: sx = w - 1 - ux; sy = h - 1 - uy; break;
        case 270: sx = w - 1 - uy; sy = ux; break;
        default: sx = ux; sy = uy; break;
    }
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_app_signsetu_camera_YuvFrames_convert(
        JNIEnv *env, jclass, jobject yBuf, jobject uBuf, jobject vBuf, jint yRow, jint uvRow, jint uvPix,
        jint w, jint h, jint rot, jint cx, jint cy, jint cw, jint ch, jobject bitmap) {
    auto *yp = static_cast<const uint8_t *>(env->GetDirectBufferAddress(yBuf));
    auto *up = static_cast<const uint8_t *>(env->GetDirectBufferAddress(uBuf));
    auto *vp = static_cast<const uint8_t *>(env->GetDirectBufferAddress(vBuf));
    if (!yp || !up || !vp) return JNI_FALSE;
    AndroidBitmapInfo info;
    if (AndroidBitmap_getInfo(env, bitmap, &info) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 || static_cast<int>(info.width) != cw || static_cast<int>(info.height) != ch) return JNI_FALSE;
    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) != ANDROID_BITMAP_RESULT_SUCCESS) return JNI_FALSE;
    for (int oy = 0; oy < ch; ++oy) {
        auto *out = reinterpret_cast<uint32_t *>(static_cast<uint8_t *>(pixels) + oy * info.stride);
        for (int ox = 0; ox < cw; ++ox) {
            int sx, sy;
            toSensor(rot, w, h, cx + ox, cy + oy, sx, sy);
            const int Y = yp[sy * yRow + sx];
            const int c = (sy >> 1) * uvRow + (sx >> 1) * uvPix;
            const int U = up[c] - 128;
            const int V = vp[c] - 128;
            // Full-range BT.601, the JFIF range Android cameras deliver; 10-bit fixed point.
            const uint8_t r = clamp8(Y + ((1436 * V) >> 10));
            const uint8_t g = clamp8(Y - ((352 * U + 731 * V) >> 10));
            const uint8_t b = clamp8(Y + ((1815 * U) >> 10));
            out[ox] = 0xFF000000u | (static_cast<uint32_t>(b) << 16) | (static_cast<uint32_t>(g) << 8) | r;
        }
    }
    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

// Fills `grid` (gw x gh, upright) with block means of luma, compares it with `prev` and writes into `out`:
// [0] motion energy, mean absolute change per block; [1] Laplacian variance over the frame's centre half;
// [2..5] the upright box, normalised, of blocks that changed by more than `moved`, or -1 when none did.
extern "C" JNIEXPORT void JNICALL
Java_app_signsetu_camera_YuvFrames_luma(
        JNIEnv *env, jclass, jobject yBuf, jint yRow, jint w, jint h, jint rot, jbyteArray prevArr, jbyteArray gridArr,
        jint gw, jint gh, jint moved, jfloatArray outArr) {
    auto *yp = static_cast<const uint8_t *>(env->GetDirectBufferAddress(yBuf));
    if (!yp) return;
    const int uw = (rot == 90 || rot == 270) ? h : w;
    const int uh = (rot == 90 || rot == 270) ? w : h;
    jbyte *prev = env->GetByteArrayElements(prevArr, nullptr);
    jbyte *grid = env->GetByteArrayElements(gridArr, nullptr);
    double energy = 0;
    int bx0 = gw, by0 = gh, bx1 = -1, by1 = -1;
    for (int gy = 0; gy < gh; ++gy) {
        for (int gx = 0; gx < gw; ++gx) {
            int sum = 0;
            // 4 x 4 samples per block: enough for a mean, a fraction of the cost of every pixel.
            for (int j = 0; j < 4; ++j) {
                for (int i = 0; i < 4; ++i) {
                    const int ux = ((gx * 4 + i) * uw + 2) / (gw * 4);
                    const int uy = ((gy * 4 + j) * uh + 2) / (gh * 4);
                    int sx, sy;
                    toSensor(rot, w, h, ux, uy, sx, sy);
                    sum += yp[sy * yRow + sx];
                }
            }
            const int mean = sum >> 4;
            const int k = gy * gw + gx;
            const int d = std::abs(mean - static_cast<uint8_t>(prev[k]));
            energy += d;
            if (d > moved) {
                if (gx < bx0) bx0 = gx;
                if (gy < by0) by0 = gy;
                if (gx > bx1) bx1 = gx;
                if (gy > by1) by1 = gy;
            }
            grid[k] = static_cast<jbyte>(mean);
        }
    }
    double s = 0, s2 = 0;
    long n = 0;
    for (int y = h / 4 + 1; y < h * 3 / 4 - 1; y += 2) {
        const uint8_t *row = yp + y * yRow;
        for (int x = w / 4 + 1; x < w * 3 / 4 - 1; x += 2) {
            const double l = row[x - 1] + row[x + 1] + row[x - yRow] + row[x + yRow] - 4.0 * row[x];
            s += l; s2 += l * l; ++n;
        }
    }
    const double m = n ? s / n : 0;
    float out[6] = {static_cast<float>(energy / (gw * gh)), n ? static_cast<float>(s2 / n - m * m) : 0.f, -1, -1, -1, -1};
    if (bx1 >= 0) {
        out[2] = static_cast<float>(bx0) / gw; out[3] = static_cast<float>(by0) / gh;
        out[4] = static_cast<float>(bx1 + 1) / gw; out[5] = static_cast<float>(by1 + 1) / gh;
    }
    env->ReleaseByteArrayElements(prevArr, prev, JNI_ABORT);
    env->ReleaseByteArrayElements(gridArr, grid, 0);
    env->SetFloatArrayRegion(outArr, 0, 6, out);
}
