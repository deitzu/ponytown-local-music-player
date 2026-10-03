package dev.deitzu.ptmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import dev.deitzu.ptmusic.audio.AudioVisualizer
import dev.deitzu.ptmusic.lyrics.LrcParser
import dev.deitzu.ptmusic.lyrics.LyricsCandidate
import dev.deitzu.ptmusic.lyrics.LyricsBundle
import dev.deitzu.ptmusic.model.Track
import dev.deitzu.ptmusic.storage.PlayerSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ThemeSpec(
    val name:String,val bg:Color,val surface:Color,val border:Color,
    val text:Color,val subtext:Color,val accent:Color
)

private val THEMES=listOf(
    ThemeSpec("Tokyo Night",Color(0xFF1A1B26),Color(0xFF16161E),Color(0xFF3B4261),Color(0xFFC0CAF5),Color(0xFF7DCFFF),Color(0xFF7AA2F7)),
    ThemeSpec("Espresso",Color(0xFF211A1E),Color(0xFF2B2124),Color(0xFF4A3B40),Color(0xFFD4C5B9),Color(0xFFB49C8C),Color(0xFFC88D75)),
    ThemeSpec("Dracula",Color(0xFF282A36),Color(0xFF21222C),Color(0xFF44475A),Color(0xFFF8F8F2),Color(0xFFFF79C6),Color(0xFFBD93F9)),
    ThemeSpec("Nord",Color(0xFF2E3440),Color(0xFF3B4252),Color(0xFF4C566A),Color(0xFFECEFF4),Color(0xFF8FBCBB),Color(0xFF88C0D0)),
    ThemeSpec("Catppuccin Mocha",Color(0xFF1E1E2E),Color(0xFF181825),Color(0xFF313244),Color(0xFFCDD6F4),Color(0xFFF5C2E7),Color(0xFFCBA6F7))
)

class MainActivity:ComponentActivity(){
    private val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        setContent{PTMusicApp(vm)}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PTMusicApp(vm:MainViewModel){
    val context=androidx.compose.ui.platform.LocalContext.current
    val tracks by vm.tracks.collectAsState()
    val query by vm.query.collectAsState()
    val settings by vm.settings.collectAsState()
    val currentId by vm.currentTrackId.collectAsState()
    val pos by vm.position.collectAsState()
    val dur by vm.duration.collectAsState()
    val playing by vm.playing.collectAsState()
    val volume by vm.volume.collectAsState()
    val shuffle by vm.shuffle.collectAsState()
    val repeat by vm.repeat.collectAsState()
    val lyrics by vm.lyrics.collectAsState()
    val sessionId by vm.audioSessionId.collectAsState()
    val theme=THEMES.getOrElse(settings.theme){THEMES.first()}

    var filters by remember{mutableStateOf<List<String>>(emptyList())}
    var settingsOpen by remember{mutableStateOf(false)}
    var fullOpen by remember{mutableStateOf(false)}
    var tagsTrack by remember{mutableStateOf<Track?>(null)}
    var lyricsTrack by remember{mutableStateOf<Track?>(null)}
    var clearConfirm by remember{mutableStateOf(false)}
    var pendingLrc by remember{mutableStateOf<Track?>(null)}
    var toastVisible by remember{mutableStateOf(false)}
    var lastToastId by remember{mutableLongStateOf(Long.MIN_VALUE)}

    val visualizer=remember{AudioVisualizer(context)}
    val levels by visualizer.levels.collectAsState()
    DisposableEffect(Unit){onDispose{visualizer.release()}}

    val permissionLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ){vm.refresh()}
    val visualPermission=rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ){granted->if(granted)vm.updateSettings{it.copy(visualizer=true)}}
    val fileLauncher=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ){uris->
        uris.forEach{runCatching{context.contentResolver.takePersistableUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION)}}
        vm.addFiles(uris)
    }
    val lrcLauncher=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val track=pendingLrc
        pendingLrc=null
        if(uri!=null&&track!=null){
            val text=runCatching{context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}.orEmpty()}.getOrDefault("")
            vm.setLyricText(track,text)
        }
    }

    LaunchedEffect(Unit){
        val p=buildList{
            add(if(Build.VERSION.SDK_INT>=33)Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE)
            if(Build.VERSION.SDK_INT>=33)add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(p.toTypedArray())
    }
    LaunchedEffect(settings.visualizer,sessionId){
        if(settings.visualizer&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)visualizer.start(sessionId)
        else visualizer.stop()
    }
    LaunchedEffect(currentId){
        tracks.firstOrNull{it.id==currentId}?.let{vm.loadLyrics(it)}
    }
    LaunchedEffect(currentId,settings.toastNotification){
        if(currentId!=null&&settings.toastNotification&&currentId!=lastToastId){
            lastToastId=currentId!!
            toastVisible=true
            delay(3000)
            toastVisible=false
        }
    }

    val current=tracks.firstOrNull{it.id==currentId}
    val filtered=remember(tracks,query,filters){
        val q=query.trim()
        val base=if(q.isBlank())tracks else tracks.filter{
            it.title.contains(q,true)||it.artist.contains(q,true)||it.album.contains(q,true)||it.tags.any{t->t.contains(q,true)}
        }
        when{
            filters.isEmpty()->base
            filters.contains("Untagged")->base.filter{it.tags.isEmpty()}
            else->base.filter{t->filters.all(t.tags::contains)}
        }
    }
    val allTags=remember(tracks){tracks.flatMap{it.tags}.distinct().sorted()}

    MaterialTheme(
        colorScheme=androidx.compose.material3.darkColorScheme(
            primary=theme.accent,secondary=theme.subtext,background=theme.bg,surface=theme.surface,
            onBackground=theme.text,onSurface=theme.text,onPrimary=Color.Black
        )
    ){
        Box(Modifier.fillMaxSize().background(theme.bg)){
            Scaffold(
                containerColor=Color.Transparent,
                topBar={
                    TopAppBar(
                        title={Text("PT Local Music Player",fontWeight=FontWeight.Bold)},
                        actions={
                            IconButton(onClick=vm::refresh){Text("↻")}
                            IconButton(onClick={settingsOpen=true}){Text("⚙")}
                        }
                    )
                },
                bottomBar={
                    MiniPlayer(
                        current,playing,pos,dur,shuffle,repeat,
                        onOpen={fullOpen=true},onPlay=vm::togglePlayPause,
                        onPrev=vm::previous,onNext=vm::next
                    )
                }
            ){padding->
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal=12.dp)){
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        OutlinedTextField(
                            value=query,onValueChange=vm::setQuery,modifier=Modifier.weight(1f),
                            singleLine=true,label={Text("Search")}
                        )
                        Button(onClick={fileLauncher.launch(arrayOf("audio/*"))}){Text("+ Add")}
                    }
                    if(allTags.isNotEmpty()){
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            allTags.forEach{tag->
                                Text(
                                    if(filters.contains(tag))"[$tag]" else tag,
                                    modifier=Modifier.clip(RoundedCornerShape(12.dp)).background(if(filters.contains(tag))theme.accent else theme.surface)
                                        .clickable{filters=if(filters.contains(tag))filters-tag else(filters.filterNot{"Untagged"::equals} + tag)},
                                    color=if(filters.contains(tag))Color.Black else theme.text,fontSize=11.sp
                                ).padding(horizontal=9.dp,vertical=5.dp)
                            }
                            Text(
                                if(filters.contains("Untagged"))"[Untagged]" else "Untagged",
                                modifier=Modifier.clip(RoundedCornerShape(12.dp)).background(if(filters.contains("Untagged"))theme.accent else theme.surface)
                                    .clickable{filters=if(filters.contains("Untagged"))emptyList() else listOf("Untagged")},
                                color=if(filters.contains("Untagged"))Color.Black else theme.text,fontSize=11.sp
                            ).padding(horizontal=9.dp,vertical=5.dp)
                            if(filters.isNotEmpty())TextButton(onClick={filters=emptyList()}){Text("Clear")}
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if(filtered.isEmpty()){
                        Column(Modifier.fillMaxWidth().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally){
                            Text("No local music found.")
                            Text("Use + Add to import files.",color=theme.subtext)
                        }
                    }else{
                        LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(bottom=150.dp)){
                            items(filtered,key=Track::id){track->
                                TrackRow(track,track.id==currentId,
                                    onPlay={vm.play(track,filtered)},
                                    onTags={tagsTrack=track},
                                    onLyrics={lyricsTrack=track},
                                    onDelete={vm.deleteTrack(track)}
                                )
                            }
                        }
                    }
                }
            }

            if(toastVisible&&current!=null){
                Surface(
                    Modifier.align(Alignment.TopCenter).padding(top=72.dp).widthIn(max=320.dp),
                    color=theme.surface,shape=RoundedCornerShape(10.dp),tonalElevation=8.dp
                ){
                    Column(Modifier.padding(10.dp)){
                        Text("NOW PLAYING • "+formatDuration(dur),color=theme.accent,fontSize=10.sp,fontWeight=FontWeight.Bold)
                        Text(current.title,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(current.artist,color=theme.subtext,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp)
                    }
                }
            }
        }
    }

    if(tagsTrack!=null)TagsDialog(tagsTrack!!,{tagsTrack=null}){tags->vm.updateTrack(tagsTrack!!.copy(tags=tags));tagsTrack=null}
    if(lyricsTrack!=null)LyricsDialog(lyricsTrack!!,vm,{lyricsTrack=null},{pendingLrc=lyricsTrack;lyricsTrack=null;lrcLauncher.launch(arrayOf("text/*","application/octet-stream"))}){candidate->vm.applyLyrics(lyricsTrack!!,candidate);lyricsTrack=null}
    if(clearConfirm)AlertDialog(
        onDismissRequest={clearConfirm=false},title={Text("Clear saved library?")},
        text={Text("This removes the app's saved metadata and imported entries. Device audio files are not deleted.")},
        confirmButton={TextButton(onClick={vm.clearAll();filters=emptyList();clearConfirm=false}){Text("Clear")}},
        dismissButton={TextButton(onClick={clearConfirm=false}){Text("Cancel")}}
    )
    if(fullOpen)ModalBottomSheet(onDismissRequest={fullOpen=false}){
        FullPlayer(current,playing,pos,dur,volume,shuffle,repeat,lyrics,settings,levels,
            vm::togglePlayPause,vm::previous,vm::next,vm::seekTo,vm::setVolume,vm::toggleShuffle,vm::cycleRepeat,
            current?.let{{vm.adjustOffset(it,-500)}} ,current?.let{{vm.adjustOffset(it,500)}} ,Modifier.navigationBarsPadding())
    }
    if(settingsOpen)SettingsSheet(
        settings,onDismiss={settingsOpen=false},onUpdate=vm::updateSettings,
        onFloating={
            if(Settings.canDrawOverlays(context))vm.enableFloating()
            else context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+context.packageName)))
        },
        onFloatingOff=vm::disableFloating,overlayAllowed=Settings.canDrawOverlays(context),
        onOpenOverlay={context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+context.packageName)))},
        onVisualizerPermission={visualPermission.launch(Manifest.permission.RECORD_AUDIO)},
        onClear={clearConfirm=true}
    )
}

@Composable
private fun TrackRow(t:Track,current:Boolean,onPlay:()->Unit,onTags:()->Unit,onLyrics:()->Unit,onDelete:()->Unit){
    var open by remember{mutableStateOf(false)}
    Card(Modifier.fillMaxWidth().padding(vertical=3.dp).clickable(onClick=onPlay),colors=CardDefaults.cardColors(containerColor=if(current)MaterialTheme.colorScheme.primary.copy(alpha=.16f) else MaterialTheme.colorScheme.surface)){
        Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(t.title,fontWeight=if(current)FontWeight.Bold else FontWeight.Normal,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(
                    buildString{append(t.artist);if(t.album.isNotBlank())append(" • ").append(t.album);if(t.tags.isNotEmpty())append(" • ").append(t.tags.joinToString(", "));if(t.lyrics.isNotBlank())append(" • LRC")},
                    fontSize=11.sp,color=MaterialTheme.colorScheme.secondary,maxLines=1,overflow=TextOverflow.Ellipsis
                )
            }
            Text(formatDuration(t.durationMs),fontSize=11.sp)
            Box{
                IconButton(onClick={open=true}){Text("⋮")}
                DropdownMenu(expanded=open,onDismissRequest={open=false}){
                    DropdownMenuItem(text={Text("Play")},onClick={open=false;onPlay()})
                    DropdownMenuItem(text={Text("Edit tags")},onClick={open=false;onTags()})
                    DropdownMenuItem(text={Text("Lyrics / LRC")},onClick={open=false;onLyrics()})
                    DropdownMenuItem(text={Text("Delete from app")},onClick={open=false;onDelete()})
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(t:Track?,playing:Boolean,pos:Long,dur:Long,shuffle:Boolean,repeat:Int,onOpen:()->Unit,onPlay:()->Unit,onPrev:()->Unit,onNext:()->Unit){
    Surface(tonalElevation=8.dp){
        Column(Modifier.fillMaxWidth().clickable(onClick=onOpen).padding(10.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(t?.title?:"Nothing playing",maxLines=1,overflow=TextOverflow.Ellipsis,fontWeight=FontWeight.Bold)
                    Text(t?.artist.orEmpty(),fontSize=11.sp,color=MaterialTheme.colorScheme.secondary)
                }
                Text((if(shuffle)"↝ "else"")+(when(repeat){1->"↻";2->"↻1";else->""}),fontSize=11.sp)
            }
            if(dur>0)LinearProgressIndicator(progress={(pos.toFloat()/dur).coerceIn(0f,1f)},Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
                IconButton(enabled=t!=null,onClick=onPrev){Text("⏮")}
                IconButton(enabled=t!=null,onClick=onPlay){Text(if(playing)"Ⅱ"else"▶")}
                IconButton(enabled=t!=null,onClick=onNext){Text("⏭")}
            }
        }
    }
}

@Composable
private fun FullPlayer(t:Track?,playing:Boolean,pos:Long,dur:Long,volume:Float,shuffle:Boolean,repeat:Int,lyrics:LyricsBundle?,settings:PlayerSettings,levels:List<Float>,onPlay:()->Unit,onPrev:()->Unit,onNext:()->Unit,onSeek:(Long)->Unit,onVolume:(Float)->Unit,onShuffle:()->Unit,onRepeat:()->Unit,minus:(()->Unit)?,plus:(()->Unit)?,modifier:Modifier){
    val theme=THEMES.getOrElse(settings.theme){THEMES.first()}
    val active=lyrics?.lines?.let{LrcParser.lineAt(it,pos)}
    Column(modifier.fillMaxWidth().padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Text(t?.title?:"Nothing playing",fontSize=20.sp,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)
        Text(t?.artist.orEmpty(),color=theme.accent)
        Text(t?.album.orEmpty(),fontSize=12.sp,color=theme.subtext)
        if(settings.visualizer)VisualizerBars(levels,theme.accent)
        if(active!=null&&settings.lrcMode!=0){
            Spacer(Modifier.height(8.dp))
            LyricCard(active,settings,theme)
            if(settings.quickOffset&&minus!=null&&plus!=null)Row(verticalAlignment=Alignment.CenterVertically){
                TextButton(onClick=minus){Text("-")}
                Text("%.1fs".format((t?.lrcOffsetMs?:0L)/1000.0),fontSize=11.sp)
                TextButton(onClick=plus){Text("+")}
            }
        }else Text(if(t==null)"Pick a track to start." else "No synced lyrics loaded",color=theme.subtext,modifier=Modifier.padding(14.dp))
        if(dur>0){
            Slider(value=(pos.toFloat()/dur).coerceIn(0f,1f),onValueChange={onSeek((dur*it).toLong())})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(formatDuration(pos),fontSize=11.sp);Text(formatDuration(dur),fontSize=11.sp)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly){
            IconButton(onClick=onShuffle){Text(if(shuffle)"🔀"else"⇄")}
            IconButton(onClick=onPrev){Text("⏮")}
            IconButton(onClick=onPlay){Text(if(playing)"Ⅱ"else"▶")}
            IconButton(onClick=onNext){Text("⏭")}
            IconButton(onClick=onRepeat){Text(if(repeat==2)"↻1"else"↻")}
        }
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            Text("Vol",modifier=Modifier.width(30.dp));Slider(value=volume,onValueChange=onVolume)
        }
    }
}

@Composable
private fun VisualizerBars(levels:List<Float>,accent:Color){
    Canvas(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f))){
        val bw=size.width/(levels.size*1.7f)
        val gap=bw*.7f
        levels.forEachIndexed{i,v->
            val h=(size.height*(0.15f+v*.85f)).coerceAtLeast(2f)
            drawRoundRect(accent,left=i*(bw+gap),top=size.height-h,right=i*(bw+gap)+bw,bottom=size.height,cornerRadius=androidx.compose.ui.geometry.CornerRadius(3f,3f))
        }
    }
}

@Composable
private fun LyricCard(line:dev.deitzu.ptmusic.lyrics.LyricLineBundle,s:PlayerSettings,t:ThemeSpec){
    val bg=when(s.lrcStyle){0->Color.Black.copy(alpha=.55f);2->Color.White.copy(alpha=.1f);else->Color.Transparent}
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bg).padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally){
        if(s.showOriginal)Text(line.original,color=Color.White,fontSize=s.lrcFontSize.sp,fontWeight=FontWeight.Bold)
        if(s.showRomanized&&line.romanized.isNotBlank())Text(line.romanized,color=t.text.copy(alpha=.88f),fontSize=s.lrcSubSize.sp)
        if(s.showTranslated&&line.translated.isNotBlank())Text(line.translated,color=t.accent.copy(alpha=.92f),fontSize=s.lrcSubSize.sp,fontStyle=FontStyle.Italic)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(s:PlayerSettings,onDismiss:()->Unit,onUpdate:((PlayerSettings)->PlayerSettings)->Unit,onFloating:()->Unit,onFloatingOff:()->Unit,overlayAllowed:Boolean,onOpenOverlay:()->Unit,onVisualizerPermission:()->Unit,onClear:()->Unit){
    val context=androidx.compose.ui.platform.LocalContext.current
    val theme=THEMES.getOrElse(s.theme){THEMES.first()}
    ModalBottomSheet(onDismissRequest=onDismiss){
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding()){
            Text("Settings",fontSize=22.sp,fontWeight=FontWeight.Bold)
            Text("Theme",color=theme.subtext,modifier=Modifier.padding(top=10.dp))
            Choice("Theme",THEMES.map{it.name},s.theme){i->onUpdate{it.copy(theme=i)}}
            Choice("LRC mode",listOf("Off","Overlay","Embedded"),s.lrcMode){i->onUpdate{it.copy(lrcMode=i)}}
            Choice("LRC style",listOf("YouTube","Glow","Glass"),s.lrcStyle){i->onUpdate{it.copy(lrcStyle=i)}}
            SliderSetting("Main font",s.lrcFontSize.toFloat(),12f,24f){v->onUpdate{it.copy(lrcFontSize=v.toInt())}}
            SliderSetting("Sub font",s.lrcSubSize.toFloat(),10f,20f){v->onUpdate{it.copy(lrcSubSize=v.toInt())}}
            SliderSetting("LRC position",s.lrcPosPercent.toFloat(),5f,50f){v->onUpdate{it.copy(lrcPosPercent=v.toInt())}}
            Toggle("Show original",s.showOriginal){onUpdate{it.copy(showOriginal=it)}}
            Toggle("Show romanized",s.showRomanized){onUpdate{it.copy(showRomanized=it)}}
            Toggle("Show translation",s.showTranslated){onUpdate{it.copy(showTranslated=it)}}
            Toggle("Auto-fetch LRCLIB",s.autoFetch){onUpdate{it.copy(autoFetch=it)}}
            Toggle("Auto-select first match",s.autoSelectLrc){onUpdate{it.copy(autoSelectLrc=it)}}
            Toggle("Auto-tag ID3 genre",s.autoTag){onUpdate{it.copy(autoTag=it)}}
            Toggle("Quick LRC offset",s.quickOffset){onUpdate{it.copy(quickOffset=it)}}
            Toggle("Track-change toast",s.toastNotification){onUpdate{it.copy(toastNotification=it)}}
            Toggle("Audio visualizer",s.visualizer){
                if(it&&ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)onVisualizerPermission()
                else onUpdate{v->v.copy(visualizer=it)}
            }
            Text("Floating UI",color=theme.subtext,modifier=Modifier.padding(top=12.dp))
            if(!overlayAllowed){
                Text("Grant overlay permission to show player above other apps.",fontSize=12.sp,color=theme.subtext)
                Button(onClick=onOpenOverlay,modifier=Modifier.fillMaxWidth()){Text("Grant overlay permission")}
            }
            Toggle("Floating player",s.floatingEnabled){if(it)onFloating()else onFloatingOff()}
            Toggle("Floating lyrics",s.floatingLyrics){onUpdate{it.copy(floatingLyrics=it)}}
            Toggle("Floating minimized",s.floatingMinimized){onUpdate{it.copy(floatingMinimized=it)}}
            SliderSetting("Idle fade delay",s.idleFade,2f,10f){v->onUpdate{it.copy(idleFade=(v*2).toInt()/2f)}}
            SliderSetting("Idle opacity",s.idleOpacity,0.1f,1f){v->onUpdate{it.copy(idleOpacity=(v*10).toInt()/10f)}}
            Spacer(Modifier.height(12.dp))
            TextButton(onClick=onClear,modifier=Modifier.fillMaxWidth()){Text("Danger: Clear saved library")}
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun Choice(label:String,options:List<String>,selected:Int,onSelected:(Int)->Unit){
    var open by remember{mutableStateOf(false)}
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
        Text(label,Modifier.weight(1f))
        Box{
            TextButton(onClick={open=true}){Text(options.getOrElse(selected){options.first()})}
            DropdownMenu(expanded=open,onDismissRequest={open=false}){
                options.forEachIndexed{i,v->DropdownMenuItem(text={Text(v)},onClick={onSelected(i);open=false})}
            }
        }
    }
}

@Composable
private fun Toggle(label:String,checked:Boolean,onChanged:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth().padding(vertical=3.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
        Text(label,Modifier.weight(1f));Switch(checked=checked,onCheckedChange=onChanged)
    }
}

@Composable
private fun SliderSetting(label:String,value:Float,min:Float,max:Float,onChange:(Float)->Unit){
    Column(Modifier.fillMaxWidth()){
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Text("%.1f".format(value),fontSize=11.sp)}
        Slider(value=value.coerceIn(min,max),onValueChange=onChange,valueRange=min..max)
    }
}

@Composable
private fun TagsDialog(t:Track,onDismiss:()->Unit,onSave:(List<String>)->Unit){
    var value by remember{mutableStateOf(t.tags.joinToString(", "))}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Edit tags")},text={
        OutlinedTextField(value=value,onValueChange={value=it},label={Text("Comma separated")},modifier=Modifier.fillMaxWidth())
    },confirmButton={TextButton(onClick={onSave(value.split(',').map(String::trim).filter(String::isNotBlank).distinct())}){Text("Save")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
}

@Composable
private fun LyricsDialog(t:Track,vm:MainViewModel,onDismiss:()->Unit,onUpload:()->Unit,onApply:(LyricsCandidate)->Unit){
    val scope=rememberCoroutineScope()
    var q by remember{mutableStateOf((t.artist+" "+t.title).trim())}
    var results by remember{mutableStateOf<List<LyricsCandidate>>(emptyList())}
    var loading by remember{mutableStateOf(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Lyrics / LRC")},text={
        Column(Modifier.widthIn(max=420.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                OutlinedTextField(value=q,onValueChange={q=it},modifier=Modifier.weight(1f),singleLine=true,label={Text("LRCLIB search")})
                TextButton(enabled=!loading,onClick={
                    loading=true
                    scope.launch{results=runCatching{vm.searchLyrics(q)}.getOrDefault(emptyList());loading=false}
                }){Text("Search")}
            }
            Button(onClick=onUpload,modifier=Modifier.fillMaxWidth().padding(top=6.dp)){Text("Upload .lrc")}
            if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.fillMaxWidth().height(240.dp)){
                items(results){r->
                    Card(Modifier.fillMaxWidth().padding(vertical=2.dp).clickable{onApply(r)}){
                        Column(Modifier.padding(8.dp)){
                            Text(r.trackName,fontWeight=FontWeight.Bold)
                            Text(r.artistName+" • "+r.albumName+" • "+(r.durationSec/60)+":"+String.format("%02d",r.durationSec%60),fontSize=11.sp)
                        }
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

private fun formatDuration(ms:Long):String{
    val s=(ms/1000).coerceAtLeast(0L)
    return (s/60).toString()+":"+String.format("%02d",s%60)
}
