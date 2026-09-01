const vm=require('node:vm'),fs=require('node:fs'),assert=require('node:assert/strict');
const nodes={};const node=()=>({value:'',checked:true,textContent:'',style:{},classList:{add(){},remove(){}},addEventListener(){},prepend(){}});
let urls=[], played=[], micStarts=0;
const context={document:{querySelector:s=>nodes[s]??=node(),querySelectorAll:()=>[],createElement:node},localStorage:{voiceReply:'false'},navigator:{onLine:true},addEventListener(){},setTimeout:fn=>{timers.push(fn);return timers.length;},clearTimeout(){},console,Date,encodeURIComponent,speechSynthesis:{cancel(){}},AndroidVoice:{playMusic(q,p){played.push([q,p])},startListening(){micStarts++},requestMic(){},stopListening(){},stopSpeaking(){},openUrl(u){urls.push(u)},speak(){}}};
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

