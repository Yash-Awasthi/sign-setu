$env:T2I = "$env:SIGNSETU_DATA\text2isl"
$hmm = "$env:T2I\hmm"; $mdl = "$env:T2I\models"
New-Item -ItemType Directory -Force $hmm, $mdl | Out-Null

# Zip 8.9 GB plus its contents: need ~20 GB free
"{0:N1} GB free on C:" -f ((Get-PSDrive C).Free / 1GB)

$files = @(
  @{ url = "https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"; out = "$mdl\face_landmarker.task" },
  @{ url = "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/latest/hand_landmarker.task"; out = "$mdl\hand_landmarker.task" },
  @{ url = "https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"; out = "$mdl\pose_landmarker.task" },
  @{ url = "https://data.mendeley.com/public-files/datasets/kcmpdxky7p/files/c14c8953-be53-4c7a-85d5-5bcd7e79d86e/file_downloaded"; out = "$hmm\ISL_CSLRT_Corpus.zip" }
)
foreach ($f in $files) {
  Write-Host "`n== $($f.out)"
  curl.exe -L -C - --retry 5 --progress-bar -o $f.out $f.url
}

$zip = "$hmm\ISL_CSLRT_Corpus.zip"
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash
if ($hash -ne "1A849C89C1D53B7E9CE43DC24F6D4F0AFD95E707BF256FEBC26716782B736686") { throw "Zip hash mismatch: $hash. Re-run to resume." }
Write-Host "Zip hash OK"

tar.exe -xvf $zip -C $hmm
Get-ChildItem $hmm -Directory | Select-Object Name
Get-ChildItem $mdl | Select-Object Name, Length
