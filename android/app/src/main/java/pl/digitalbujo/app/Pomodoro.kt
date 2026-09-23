package pl.digitalbujo.app
import android.content.Context
import org.json.JSONObject
class Pomodoro(context: Context) {
    private val prefs=context.getSharedPreferences("pomodoro",Context.MODE_PRIVATE)
    fun read():JSONObject?=prefs.getString("timer",null)?.let {JSONObject(it)}
    fun remaining(t:JSONObject)=if(t.optBoolean("running"))maxOf(0,t.getLong("deadline")-System.currentTimeMillis())else t.getLong("remaining")
    fun start(profile:String,task:String,title:String,minutes:Int){require(minutes in 5..30){"Choose a whole number from 5 to 30 minutes."};save(JSONObject().put("profileId",profile).put("taskId",task).put("title",title).put("minutes",minutes).put("remaining",minutes*60000L).put("deadline",System.currentTimeMillis()+minutes*60000L).put("running",true))}
    fun pause(){val t=read() ?: return;t.put("remaining",remaining(t)).put("running",false);save(t)}
    fun resume(){val t=read() ?: return;val left=remaining(t);require(left>0){"Start a new focus session."};t.put("deadline",System.currentTimeMillis()+left).put("running",true);save(t)}
    fun reset(){prefs.edit().remove("timer").apply()}
    private fun save(t:JSONObject){prefs.edit().putString("timer",t.toString()).apply()}
}
