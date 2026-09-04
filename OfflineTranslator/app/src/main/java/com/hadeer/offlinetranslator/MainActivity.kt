package com.hadeer.offlinetranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.*
import dev.ffmpegkit.llama.Llama
import dev.ffmpegkit.llama.LlamaConfig
import dev.ffmpegkit.llama.LlamaModel
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import kotlinx.coroutines.*
import java.io.File
import java.text.DecimalFormat
import java.util.Locale
import kotlin.math.min

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var statusText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var sourceText: EditText
    private lateinit var translatedText: EditText
    private lateinit var understandingHint: TextView
    private lateinit var recordArabicButton: Button
    private lateinit var recordPersianButton: Button
    private lateinit var stopRecordButton: Button
    private lateinit var arToFaButton: Button
    private lateinit var faToArButton: Button
    private lateinit var speakButton: Button
    private lateinit var usdIqdRate: EditText
    private lateinit var usdTomanRate: EditText
    private lateinit var moneyAmount: EditText
    private lateinit var moneyResult: TextView
    private lateinit var noteDenomination: EditText
    private lateinit var ocrResult: TextView
    private lateinit var currencyImage: ImageView

    private var llamaModel: LlamaModel? = null
    private var whisperModel: WhisperModel? = null
    private var recorder: WavRecorder? = null
    private var recordMode = RecordMode.NONE
    private var modelsReady = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastSpeakLanguage = "fa"
    private val prefs by lazy { getSharedPreferences("offline_translator", MODE_PRIVATE) }

    enum class RecordMode { NONE, AR_TO_FA, FA_TO_AR, MONEY_AR, MONEY_FA }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        loadSavedRates()
        setupTts()
        setupActions()
        setModelControlsEnabled(false)
        prepareOfflineEngines()
    }

    private fun bindViews() {
        statusText = findViewById(R.id.statusText)
        progressBar = findViewById(R.id.progressBar)
        sourceText = findViewById(R.id.sourceText)
        translatedText = findViewById(R.id.translatedText)
        understandingHint = findViewById(R.id.understandingHint)
        recordArabicButton = findViewById(R.id.recordArabicButton)
        recordPersianButton = findViewById(R.id.recordPersianButton)
        stopRecordButton = findViewById(R.id.stopRecordButton)
        arToFaButton = findViewById(R.id.arToFaButton)
        faToArButton = findViewById(R.id.faToArButton)
        speakButton = findViewById(R.id.speakButton)
        usdIqdRate = findViewById(R.id.usdIqdRate)
        usdTomanRate = findViewById(R.id.usdTomanRate)
        moneyAmount = findViewById(R.id.moneyAmount)
        moneyResult = findViewById(R.id.moneyResult)
        noteDenomination = findViewById(R.id.noteDenomination)
        ocrResult = findViewById(R.id.ocrResult)
        currencyImage = findViewById(R.id.currencyImage)
    }

    private fun setupActions() {
        recordArabicButton.setOnClickListener { startVoice(RecordMode.AR_TO_FA) }
        recordPersianButton.setOnClickListener { startVoice(RecordMode.FA_TO_AR) }
        stopRecordButton.setOnClickListener { stopVoiceAndProcess() }
        arToFaButton.setOnClickListener { translateManual("ar", "fa") }
        faToArButton.setOnClickListener { translateManual("fa", "ar") }
        speakButton.setOnClickListener {
            translatedText.text.toString().trim().takeIf { it.isNotEmpty() }?.let { speakLocal(it, lastSpeakLanguage) }
        }
        findViewById<Button>(R.id.saveRatesButton).setOnClickListener { saveRates() }
        findViewById<Button>(R.id.parseTomanButton).setOnClickListener { calculateMoney("toman") }
        findViewById<Button>(R.id.parseRialButton).setOnClickListener { calculateMoney("rial") }
        findViewById<Button>(R.id.moneyVoiceFaButton).setOnClickListener { startVoice(RecordMode.MONEY_FA) }
        findViewById<Button>(R.id.moneyVoiceArButton).setOnClickListener { startVoice(RecordMode.MONEY_AR) }
        findViewById<Button>(R.id.cameraButton).setOnClickListener { openCamera() }
        findViewById<Button>(R.id.galleryButton).setOnClickListener { openGallery() }
        findViewById<Button>(R.id.banknoteCountButton).setOnClickListener { calculateBanknoteCount() }
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
        }
    }

    private fun prepareOfflineEngines() {
        scope.launch {
            try {
                statusText.text = "أول تشغيل: تجهيز نماذج العمل المحلي…"
                progressBar.progress = 2
                val modelFile = File(filesDir, "models/qwen.gguf")
                withContext(Dispatchers.IO) {
                    copyLargeAsset("models/qwen.gguf", modelFile, 5, 72)
                    OcrEngine.prepare(this@MainActivity)
                }
                progressBar.progress = 76
                statusText.text = "تحميل محرك الترجمة…"
                llamaModel = Llama.loadModel(
                    modelFile.absolutePath,
                    LlamaConfig(
                        contextSize = 1024,
                        threads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),
                        temperature = 0.1f,
                        topP = 0.85f,
                        topK = 20,
                        seed = 7
                    )
                )
                progressBar.progress = 88
                statusText.text = "تحميل التعرف الصوتي المحلي…"
                whisperModel = Whisper.loadModelFromAsset(this@MainActivity, "models/ggml-tiny.bin")
                progressBar.progress = 100
                modelsReady = true
                setModelControlsEnabled(true)
                statusText.text = "جاهز — الترجمة والصوت يعملان محليًا بدون إنترنت."
            } catch (e: Exception) {
                modelsReady = false
                setModelControlsEnabled(false)
                progressBar.visibility = View.GONE
                statusText.text = "تعذر تجهيز نموذج محلي: ${e.message ?: e.javaClass.simpleName}. حساب النقود اليدوي ما زال يعمل."
            }
        }
    }

    private fun copyLargeAsset(assetName: String, dest: File, fromPercent: Int, toPercent: Int) {
        dest.parentFile?.mkdirs()
        val total = try { assets.openFd(assetName).length } catch (_: Exception) { -1L }
        if (dest.exists() && total > 0 && dest.length() == total) {
            runOnUiThread { progressBar.progress = toPercent }
            return
        }
        val temp = File(dest.parentFile, dest.name + ".part")
        temp.delete()
        assets.open(assetName).use { input ->
            temp.outputStream().buffered(4 * 1024 * 1024).use { output ->
                val buffer = ByteArray(4 * 1024 * 1024)
                var copied = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    copied += read
                    if (total > 0) {
                        val p = fromPercent + ((copied.toDouble() / total) * (toPercent - fromPercent)).toInt()
                        runOnUiThread { progressBar.progress = p.coerceIn(fromPercent, toPercent) }
                    }
                }
            }
        }
        dest.delete()
        if (!temp.renameTo(dest)) {
            temp.copyTo(dest, overwrite = true)
            temp.delete()
        }
    }

    private fun setModelControlsEnabled(enabled: Boolean) {
        listOf(
            recordArabicButton, recordPersianButton, arToFaButton, faToArButton, speakButton,
            findViewById<Button>(R.id.moneyVoiceFaButton), findViewById<Button>(R.id.moneyVoiceArButton),
            findViewById<Button>(R.id.cameraButton), findViewById<Button>(R.id.galleryButton)
        ).forEach { it.isEnabled = enabled }
    }

    private fun startVoice(mode: RecordMode) {
        if (!modelsReady) return toast("انتظر حتى يكتمل تجهيز النماذج المحلية")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO_PERMISSION)
            return toast("اسمح بالميكروفون ثم اضغط زر الكلام مرة أخرى")
        }
        if (recorder?.isRecording() == true) return
        recordMode = mode
        val wav = File(cacheDir, "speech_${System.currentTimeMillis()}.wav")
        try {
            recorder = WavRecorder(wav).also { it.start() }
            stopRecordButton.isEnabled = true
            recordArabicButton.isEnabled = false
            recordPersianButton.isEnabled = false
            findViewById<Button>(R.id.moneyVoiceFaButton).isEnabled = false
            findViewById<Button>(R.id.moneyVoiceArButton).isEnabled = false
            statusText.text = if (mode == RecordMode.AR_TO_FA || mode == RecordMode.MONEY_AR)
                "أسمع العربية الآن… اضغط إيقاف عند الانتهاء."
            else "در حال شنیدن فارسی… بعد از پایان، توقف را بزنید."
        } catch (e: Exception) {
            recorder = null
            recordMode = RecordMode.NONE
            statusText.text = "تعذر بدء التسجيل: ${e.message}"
        }
    }

    private fun stopVoiceAndProcess() {
        val active = recorder ?: return
        val mode = recordMode
        active.stop()
        val file = active.outputFile
        recorder = null
        stopRecordButton.isEnabled = false
        setModelControlsEnabled(modelsReady)
        if (!file.exists() || file.length() < 1000L) {
            statusText.text = "لم يصل صوت كافٍ. حاول مرة أخرى وتحدث بوضوح."
            return
        }
        scope.launch {
            transcribeAndProcess(file, mode)
            file.delete()
        }
    }

    private suspend fun transcribeAndProcess(audioFile: File, mode: RecordMode) {
        val model = whisperModel ?: return
        try {
            statusText.text = "تحويل الصوت إلى نص محليًا…"
            val lang = if (mode == RecordMode.AR_TO_FA || mode == RecordMode.MONEY_AR) "ar" else "fa"
            val result = Whisper.transcribe(
                model,
                audioFile.absolutePath,
                WhisperConfig(language = lang, translate = false, threads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)))
            )
            val text = result.text.trim()
            if (text.length < 2 || text.contains("[BLANK", ignoreCase = true)) {
                understandingHint.text = "🔴 لم أفهم الكلام بوضوح. أعد نطقه ببطء أو اكتبه يدويًا."
                statusText.text = "لم أفهم الصوت بوضوح."
                return
            }
            understandingHint.text = if (text.length < 5)
                "🟡 الكلام قصير؛ تحقق من الكلمة قبل الاعتماد على الترجمة."
            else "🟢 تم التعرف على الكلام. يمكنك تعديل أي كلمة قبل إعادة الترجمة."
            when (mode) {
                RecordMode.AR_TO_FA -> { sourceText.setText(text); translateText(text, "ar", "fa", true) }
                RecordMode.FA_TO_AR -> { sourceText.setText(text); translateText(text, "fa", "ar", true) }
                RecordMode.MONEY_AR, RecordMode.MONEY_FA -> {
                    moneyAmount.setText(text)
                    calculateMoney(MoneyUtils.detectCurrency(text))
                    statusText.text = "تمت قراءة المبلغ من الصوت."
                }
                else -> Unit
            }
        } catch (e: Exception) {
            statusText.text = "خطأ في التعرف الصوتي: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            recordMode = RecordMode.NONE
        }
    }

    private fun translateManual(sourceLang: String, targetLang: String) {
        val text = sourceText.text.toString().trim()
        if (text.isEmpty()) return toast("اكتب أو سجل الكلام أولًا")
        scope.launch { translateText(text, sourceLang, targetLang, false) }
    }

    private suspend fun translateText(text: String, sourceLang: String, targetLang: String, autoSpeak: Boolean) {
        val model = llamaModel ?: return
        try {
            statusText.text = "جاري الترجمة على الهاتف…"
            val systemPrompt = if (sourceLang == "ar" && targetLang == "fa") {
                "You are an offline Arabic-to-Persian translator. Translate Iraqi colloquial Arabic and Modern Standard Arabic into natural everyday Persian. Understand Iraqi slang such as ماكو, شكد, وين, هسه, اريد, خوش. Preserve names, numbers and currency values. Output ONLY the Persian translation, no labels or explanation."
            } else {
                "You are an offline Persian-to-Arabic translator. Translate everyday spoken Persian into clear Iraqi Arabic. Preserve names, numbers and currency values. Output ONLY the Arabic translation, no labels or explanation."
            }
            val result = Llama.complete(model, text, systemPrompt = systemPrompt, maxTokens = 160)
            val cleaned = result.text.trim().removePrefix("Translation:").removePrefix("الترجمة:").removePrefix("ترجمه:").trim().trim('"', '\'', '«', '»')
            if (cleaned.isBlank()) {
                translatedText.setText("لم أستطع تكوين ترجمة واضحة. صحح النص أو أعد نطق الجملة.")
                understandingHint.text = "🟡 الترجمة غير مؤكدة."
                return
            }
            translatedText.setText(cleaned)
            lastSpeakLanguage = targetLang
            statusText.text = "تمت الترجمة محليًا."
            if (autoSpeak) speakLocal(cleaned, targetLang)
        } catch (e: Exception) {
            statusText.text = "خطأ في الترجمة المحلية: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun speakLocal(text: String, language: String) {
        if (!ttsReady) return toast("محرك النطق المحلي غير جاهز")
        val engine = tts ?: return
        val locale = if (language == "fa") Locale("fa", "IR") else Locale("ar", "IQ")
        engine.language = locale
        val offlineVoice = engine.voices?.firstOrNull {
            it.locale.language == locale.language && !it.isNetworkConnectionRequired
        }
        if (offlineVoice == null) {
            statusText.text = "لا يوجد صوت ${if (language == "fa") "فارسي" else "عربي"} أوفلاين مثبت على الهاتف. الترجمة النصية تعمل."
            return
        }
        engine.voice = offlineVoice
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "translation")
    }

    private fun loadSavedRates() {
        usdIqdRate.setText(prefs.getString("usd_iqd", "") ?: "")
        usdTomanRate.setText(prefs.getString("usd_toman", "") ?: "")
    }

    private fun saveRates() {
        val iqd = parseRate(usdIqdRate)
        val toman = parseRate(usdTomanRate)
        if (iqd == null || toman == null || iqd <= 0 || toman <= 0) return toast("أدخل سعر الدولار بالدينار والتومان بشكل صحيح")
        prefs.edit().putString("usd_iqd", iqd.toString()).putString("usd_toman", toman.toString()).apply()
        toast("تم حفظ سعر الصرف محليًا")
    }

    private fun parseRate(field: EditText): Double? = MoneyUtils.normalizeDigits(field.text.toString()).replace(",", "").toDoubleOrNull()

    private fun getRates(): MoneyUtils.Rates? {
        val iqd = parseRate(usdIqdRate)
        val toman = parseRate(usdTomanRate)
        return if (iqd != null && toman != null && iqd > 0 && toman > 0) MoneyUtils.Rates(iqd, toman) else null
    }

    private fun calculateMoney(forcedCurrency: String) {
        val raw = moneyAmount.text.toString().trim()
        val amount = MoneyUtils.parseAmount(raw)
        if (amount == null) {
            moneyResult.text = "لم أفهم المبلغ. جرّب كتابته بالأرقام مثل: 200000 تومان."
            return
        }
        val rates = getRates()
        if (rates == null) {
            moneyResult.text = "أدخل سعر الصرف اليدوي واحفظه أولًا.\nالمبلغ المقروء: ${DecimalFormat("#,##0").format(amount)}"
            return
        }
        val currency = when {
            raw.contains("ریال") || raw.contains("ريال") -> "rial"
            raw.contains("تومان") || raw.contains("تومن") -> "toman"
            else -> forcedCurrency
        }
        val conversion = MoneyUtils.convert(amount.toDouble(), currency, rates)
        moneyResult.text = if (conversion == null) "تعذر الحساب. تحقق من أسعار الصرف." else
            "المبلغ المقروء: ${DecimalFormat("#,##0").format(amount)} ${currencyLabel(currency)}\n\n${MoneyUtils.formatConversion(conversion)}\n\nالسعر المستخدم:\n1$ = ${DecimalFormat("#,##0.##").format(rates.usdToIqd)} د.ع\n1$ = ${DecimalFormat("#,##0.##").format(rates.usdToToman)} تومان"
    }

    private fun currencyLabel(c: String): String = when (c) {
        "rial" -> "ريال إيراني"
        "toman" -> "تومان"
        "usd" -> "دولار"
        "iqd" -> "دينار عراقي"
        else -> c
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) startActivityForResult(intent, REQ_CAMERA)
        else toast("لا يوجد تطبيق كاميرا متاح")
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "اختر صورة العملة"), REQ_GALLERY)
    }

    @Deprecated("Compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_CAMERA -> {
                val bitmap = data?.extras?.get("data") as? Bitmap
                if (bitmap != null) analyzeCurrencyImage(bitmap) else toast("تعذر قراءة صورة الكاميرا")
            }
            REQ_GALLERY -> {
                val uri = data?.data ?: return
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                        if (bitmap != null) analyzeCurrencyImage(scaleForOcr(bitmap))
                    }
                } catch (e: Exception) {
                    ocrResult.text = "تعذر فتح الصورة: ${e.message}"
                }
            }
        }
    }

    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 1800) return bitmap
        val ratio = 1800f / maxSide.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private fun analyzeCurrencyImage(bitmap: Bitmap) {
        currencyImage.setImageBitmap(bitmap)
        ocrResult.text = "جاري قراءة الورقة محليًا…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) { OcrEngine.recognize(this@MainActivity, bitmap) }
                val denomination = MoneyUtils.likelyBanknoteDenomination(result.text)
                if (denomination == null) {
                    ocrResult.text = "لم أتعرف على فئة واضحة.\nثقة القراءة: ${result.confidence}%\nالنص المقروء:\n${result.text.ifBlank { "لا يوجد نص واضح" }}\n\nاكتب فئة الورقة يدويًا ثم اضغط حساب عدد الأوراق."
                    return@launch
                }
                val detectedCurrency = MoneyUtils.detectCurrency(result.text, true)
                val noteRial = if (detectedCurrency == "toman") denomination * 10L else denomination
                noteDenomination.setText(noteRial.toString())
                val target = targetTomanFromInput()
                val count = if (target != null) MoneyUtils.banknoteCount(target, noteRial) else "أدخل المبلغ المطلوب في قسم النقود لحساب عدد الأوراق."
                ocrResult.text = "الفئة المحتملة: ${DecimalFormat("#,##0").format(noteRial)} ريال = ${DecimalFormat("#,##0").format(noteRial / 10)} تومان\nثقة OCR: ${result.confidence}%\n\n$count\n\nالنص المقروء:\n${result.text}"
            } catch (e: Exception) {
                ocrResult.text = "تعذر تحليل الصورة: ${e.message ?: e.javaClass.simpleName}\nيمكنك إدخال فئة الورقة يدويًا."
            }
        }
    }

    private fun calculateBanknoteCount() {
        val noteRial = MoneyUtils.normalizeDigits(noteDenomination.text.toString()).replace(",", "").toLongOrNull()
        val target = targetTomanFromInput()
        ocrResult.text = when {
            noteRial == null || noteRial <= 0 -> "أدخل فئة الورقة بالريال الإيراني أولًا."
            target == null || target <= 0 -> "أدخل المبلغ المطلوب في قسم النقود أولًا."
            else -> MoneyUtils.banknoteCount(target, noteRial)
        }
    }

    private fun targetTomanFromInput(): Long? {
        val raw = moneyAmount.text.toString()
        val amount = MoneyUtils.parseAmount(raw) ?: return null
        return when (MoneyUtils.detectCurrency(raw)) {
            "rial" -> amount / 10L
            "usd" -> getRates()?.let { (amount * it.usdToToman).toLong() }
            "iqd" -> getRates()?.let { ((amount / it.usdToIqd) * it.usdToToman).toLong() }
            else -> amount
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        try { recorder?.stop() } catch (_: Exception) {}
        whisperModel?.let { try { Whisper.releaseModel(it) } catch (_: Exception) {} }
        llamaModel?.let { try { Llama.releaseModel(it) } catch (_: Exception) {} }
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val REQ_AUDIO_PERMISSION = 100
        private const val REQ_CAMERA = 101
        private const val REQ_GALLERY = 102
    }
}
