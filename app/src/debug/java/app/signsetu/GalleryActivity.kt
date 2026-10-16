package app.signsetu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.signsetu.data.content.ContentBundle
import app.signsetu.ui.avatar.Avatar
import app.signsetu.ui.avatar.Character
import app.signsetu.ui.avatar.Timeline

/** `adb shell am start -n in.signsetu/app.signsetu.GalleryActivity --es c adult --es only mother.bindi,home.roof --ef f 0.5` */
class GalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val character = if (intent.getStringExtra("c") == "adult") Character.ADULT else Character.CHILD
        val only = intent.getStringExtra("only")?.split(",")?.toSet()
        val fraction = intent.getFloatExtra("f", -1f)
        val columns = intent.getIntExtra("cols", 3)
        val repo = (application as SignSetuApp).container.content
        setContent {
            val bundle by produceState<ContentBundle?>(null) { value = repo.load() }
            val gestures = bundle?.gestures?.values?.filter { only == null || it.id in only }.orEmpty()
            LazyVerticalGrid(GridCells.Fixed(columns), Modifier.background(Color(0xFFFFF4E8))) {
                items(gestures, key = { it.id }) { g ->
                    // The middle key is usually the moment of contact.
                    val keys = g.keys.sortedBy { it.t }
                    val at = if (fraction >= 0f) Timeline.LEAD_IN + keys.last().t * fraction else Timeline.LEAD_IN + keys[keys.size / 2].t
                    Column(Modifier.padding(4.dp).background(Color(0xFFFFE6CC), RoundedCornerShape(12.dp))) {
                        Avatar(character, g, g.id, Modifier.fillMaxWidth().aspectRatio(1f), atSeconds = at)
                        Text(g.id, fontSize = 10.sp, modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}
