package app.signsetu.ui.session

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.R
import app.signsetu.data.content.ContentBundle
import app.signsetu.data.content.or
import app.signsetu.ui.components.CardImage
import app.signsetu.ui.theme.Baloo

/** The people a frightened person most often asks for; every id is a content card, so the words exist in the
 * person's language and each tile shows its picture. */
internal val FAMILY_ROLES = listOf("mother", "father", "brother", "sister", "friends", "someone_else")

/** The answer's cue when the person points at who to call: "call:<role>". */
internal fun callCue(role: String): String = "call:$role"

/** The role a cue names, or null when the cue is not a call. */
internal fun calledRole(cue: String?): String? {
    val f = cue?.removePrefix("cue:")?.split(':') ?: return null
    if (f.size != 2 || f[0] != "call" || f[1] !in FAMILY_ROLES) return null
    return f[1]
}

/** Who to call, as large pictures the person can point at instead of naming anyone aloud. A tap speaks the role and
 * sends it as the answer; the officer places the call. */
@Composable
internal fun FamilyCallCard(content: ContentBundle, lang: String, onCall: (String) -> Unit, modifier: Modifier = Modifier) {
    val ink = Color(0xFF4F4539)
    val fill = Color(0xFFD6E4FF)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.call_who), style = MaterialTheme.typography.labelLarge, color = ink)
        for (row in FAMILY_ROLES.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in row) {
                    val label = content.cards[id]?.label?.or(lang) ?: id
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = fill,
                        border = BorderStroke(1.dp, ink.copy(alpha = 0.25f)),
                        modifier = Modifier.weight(1f).clickable(role = Role.Button) { onCall(id) }.semantics { contentDescription = label },
                    ) {
                        Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            CardImage(id, Modifier.size(48.dp))
                            Text(
                                label,
                                fontFamily = Baloo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = ink,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Icon(Icons.Filled.Call, contentDescription = null, tint = ink, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

/** The roles' labels are content cards, so no string here names a person. */
