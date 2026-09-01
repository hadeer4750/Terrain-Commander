const vm=require('node:vm'),fs=require('node:fs'),assert=require('node:assert/strict');
const nodes={};const node=()=>({value:'',checked:true,textContent:'',style:{},classList:{add(){},remove(){}},addEventListener(){},prepend(){},appendChild(){}});
let urls=[], played=[], controls=[], micStarts=0;
const context={document:{querySelector:s=>nodes[s]??=node(),querySelectorAll:()=>[],createElement:node},localStorage:{voiceReply:'false',music:'youtube_music'},navigator:{onLine:true},addEventListener(){},setTimeout:fn=>{timers.push(fn);return timers.length;},clearTimeout(){},setInterval(){},console,Date,encodeURIComponent,speechSynthesis:{cancel(){}},AndroidVoice:{getLibrary(){return '[]'},getMusicState(){return '{}'},musicControl(a){controls.push(a)},playMusic(q,p){played.push([q,p])},startListening(){micStarts++},requestMic(){},stopListening(){},stopSpeaking(){},openUrl(u){urls.push(u)},speak(){}}};
let timers=[];context.window=context;vm.createContext(context);vm.runInContext([...fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/index.html'),'utf8').matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]).join('\n'),context);
vm.runInContext("execute('اتصل ٠٧٧١٢٣٤٥٦٧٨')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.pop(),'tel:07712345678');
vm.runInContext("execute('واتساب 07712345678')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.pop(),'https://wa.me/9647712345678');
nodes['#listenBtn'].onclick();assert.equal(micStarts,0);context.NativeVoice.onReady();assert.equal(micStarts,1);
context.NativeVoice.onError('permission denied',true);assert.equal(vm.runInContext('listeningWanted',context),false);
nodes['#listenBtn'].onclick();context.NativeVoice.onPause();assert.equal(vm.runInContext('listeningWanted',context),false);

vm.runInContext("execute('وصلني للعمل')",context);assert.match(nodes['#status'].textContent,/أدخل عنوان/);assert.equal(urls.length,0);
nodes['#work'].value='شركة البصرة شارع الجزائر';nodes['#saveBtn'].onclick();assert.equal(context.localStorage.work,'شركة البصرة شارع الجزائر');
vm.runInContext("execute('وديني للدوام')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.pop(),'https://www.google.com/maps/dir/?api=1&destination='+encodeURIComponent('شركة البصرة شارع الجزائر'));
vm.runInContext("execute('احفظ مكان عملي في البصرة العشار')",context);assert.equal(context.localStorage.work,'البصرة العشار');
vm.runInContext("execute('شغّل أغنية يا طيور كاظم الساهر')",context);timers.splice(0).forEach(fn=>fn());assert.deepEqual(played.pop(),['يا طيور كاظم الساهر','youtube_music']);assert.equal(urls.length,0);
vm.runInContext("execute('شغل موسيقى')",context);timers.splice(0).forEach(fn=>fn());assert.equal(played.length,0);
vm.runInContext("execute('ابحث عن أغنية يا طيور')",context);timers.splice(0).forEach(fn=>fn());assert.match(urls.pop(),/^https:\/\/music.youtube.com\/search/);
console.log('PASS: work missing/saved/navigation/voice-save, native song playback, empty query, explicit search, previous phone and microphone cases');


nodes['#music'].value='youtube';nodes['#saveBtn'].onclick();assert.equal(context.localStorage.music,'youtube');
vm.runInContext("execute('شغل أغنية يا طيور')",context);timers.splice(0).forEach(fn=>fn());assert.deepEqual(played.pop(),['يا طيور','youtube']);
vm.runInContext("execute('ابحث عن أغنية يا طيور')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.pop(),'https://www.youtube.com/results?search_query='+encodeURIComponent('يا طيور'));
const reloaded={...context,window:null};reloaded.window=reloaded;vm.createContext(reloaded);vm.runInContext([...fs.readFileSync(require('node:path').join(__dirname,'../app/src/main/assets/index.html'),'utf8').matchAll(/<script>([\s\S]*?)<\/script>/g)].map(m=>m[1]).join('\n'),reloaded);assert.equal(vm.runInContext('cfg.music',reloaded),'youtube');
console.log('PASS: YouTube selection, playback dispatch, search URL, persistence across reload');

vm.runInContext("cfg.music='local';$('#music').value='local';execute('شغل أغنية يا طيور')",context);assert.deepEqual(played.pop(),['يا طيور','local']);assert.equal(urls.length,0);
vm.runInContext("cfg.music='system_local';$('#music').value='system_local';execute('شغل موسيقى')",context);assert.deepEqual(played.pop(),['','system_local']);
vm.runInContext("execute('وقف الموسيقى');execute('كمل');execute('التالي');execute('السابق')",context);assert.deepEqual(controls,['pause','resume','next','previous']);
vm.runInContext("execute('ابحث عن أغنية يا طيور')",context);assert.equal(nodes['#trackSearch'].value,'يا طيور');assert.equal(urls.length,0);
console.log('PASS: internal/default file playback, generic play, local search, transport commands');

vm.runInContext("cfg.music='local';$('#music').value='local';execute('تشغيل موسيقى')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.length,0,'Music request must never fall through to web search');assert.deepEqual(played.pop(),['','local']);

for(const provider of ['local','system_local']){
  nodes['#music'].value=provider; // No save button or change event: use visible selection.
  for(const phrase of ['تشغيل الموسيقى','شغل الموسيقى','أريد تشغيل موسيقى','ممكن تشغل موسيقى','شغّل لي أغنية يا طيور','ممكن تشغيل موسيقى','لو سمحت تشغيل أغنية يا طيور','شغلي أغنية يا طيور','الموسيقى']){
    const before=played.length;vm.runInContext('execute('+JSON.stringify(phrase)+')',context);timers.splice(0).forEach(fn=>fn());
    assert.equal(urls.length,0,phrase+' unexpectedly opened the web');
    const expected=phrase.includes('طيور')?'يا طيور':'';assert.deepEqual(played.pop(),[expected,provider],phrase);
  }
}
vm.runInContext("execute('طلب صوتي غير مفهوم')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.length,0);
vm.runInContext("execute('ابحث عن الطقس في البصرة')",context);timers.splice(0).forEach(fn=>fn());assert.match(urls.pop(),/^https:\/\/www.google.com\/search/);
console.log('PASS: تشغيل موسيقى variants, live player selection without save, unknown command cannot open web, explicit web search retained');

vm.runInContext("execute('افتح واتساب 07712345678')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.pop(),'https://wa.me/9647712345678');
vm.runInContext("execute('البحث عن أغنية يا طيور')",context);timers.splice(0).forEach(fn=>fn());assert.equal(urls.length,0);assert.equal(nodes['#trackSearch'].value,'يا طيور');
console.log('PASS: open WhatsApp remains a messaging command; البحث عن أغنية stays local');
