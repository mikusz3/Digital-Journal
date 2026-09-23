package pl.digitalbujo.app
import android.app.Activity
import android.app.AlertDialog
import android.widget.*
import org.json.JSONObject
object SpreadLayouts {
    fun show(activity:Activity,spread:JSONObject){
        val appearance=Appearance(activity)
        val layout=spread.optJSONObject("layout");val kind=layout?.optString("kind") ?: "blank"
        val body=LinearLayout(activity).apply {orientation=LinearLayout.VERTICAL;setPadding(24,20,24,20);setBackgroundColor(appearance.color("surface"))}
        fun text(value:String){body.addView(TextView(activity).apply {text=I18n.t(activity,value);setTextColor(appearance.color("text"));textSize=16f;setPadding(4,12,4,12)})}
        text(spread.getString("title"));text("A guide to drawing this spread on paper.")
        val labels=when(kind){"calendar"->listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")+List(35){" "};"tracker"->(1..31).map {it.toString()};"log"->listOf("Date","Entry","Reflection")+List(15){" "};"wishlist"->listOf("Item or experience","Why it matters","Notes")+List(15){" "};else->emptyList()}
        if(kind=="calendar")text("Month / Year")
        if(kind=="tracker")text("What I want to track")
        if(labels.isNotEmpty()){val columns=if(kind in listOf("calendar","tracker"))7 else 3;val table=TableLayout(activity).apply {isStretchAllColumns=true};labels.chunked(columns).forEach {values->val row=TableRow(activity);values.forEach {value->row.addView(TextView(activity).apply {text=I18n.t(activity,value);setTextColor(appearance.color("text"));textSize=11f;setPadding(4,16,4,16);background=android.graphics.drawable.GradientDrawable().apply {setColor(appearance.color("surface"));setStroke(1,appearance.color("text"))}},TableRow.LayoutParams(0,-2,1f))};table.addView(row)};body.addView(table)}
        text(layout?.optString("notes") ?: "")
        AlertDialog.Builder(activity).setTitle(I18n.t(activity,"Spread template")).setView(ScrollView(activity).apply {addView(body)}).setPositiveButton(I18n.t(activity,"Close"),null).show()
    }
}
