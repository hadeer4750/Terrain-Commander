package com.hadeer.offlinetranslator

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.View
import android.widget.*
import androidx.core.content.FileProvider
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
    private lateinit var sourceLanguageSpinner: Spinner
    private lateinit var targetLanguageSpinner: Spinner
    private lateinit var conversationModeButton: Button
    private lateinit var recordVoiceButton: Button
    private lateinit var stopRecordButton: Button
    private lateinit var translateButton: Button
    private lateinit var swapButton: Button
    private lateinit var speakButton: Button
    private lateinit var usdIqdRate: EditText
    private lateinit var usdIrrRate: EditText
    private lateinit var moneyAmount: EditText
    private lateinit var moneyResult: TextView
    private lateinit var noteDenomination: EditText
    private lateinit var ocrResult: TextView
    private lateinit var priceImage: ImageView

    private var llamaModel: LlamaModel? = null
    private var whisperModel: WhisperModel? = null
    private var recorder: WavRecorder? = null
    private var recordMode = RecordMode.NONE
    private var currentRecordLanguage = "ar"
    private var modelsReady = false
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastSpeakLanguage = "fa"
    private var conversationMode = false
    private var pendingListenLanguage: String? = null
    private var pendingSwapBeforeListen = false
    private var cameraImageFile: File? = null
    private val prefs by lazy { getSharedPreferences("offline_translator_v2", MODE_PRIVATE) }

    private val languages = listOf(
        LanguageItem("العربية", "ar"),
        LanguageItem("English", "en"),
        LanguageItem("فارسی", "fa")
    )

    data class LanguageItem(val label: String, val code: String)
    enum class RecordMode { NONE, TRANSLATE, MONEY }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupConversationButton()
        setupLanguageSelectors()
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
        sourceLanguageSpinner = findViewById(R.id.sourceLanguageSpinner)
        targetLanguageSpinner = findViewById(R.id.targetLanguageSpinner)
        recordVoiceButton = findViewById(R.id.recordVoiceButton)
        stopRecordButton = findViewById(R.id.stopRecordButton)
        translateButton = findViewById(R.id.translateButton)
        swapButton = findViewById(R.id.swapButton)
        speakButton = findViewById(R.id.speakButton)
        usdIqdRate = findViewById(R.id.usdIqdRate)
        usdIrrRate = findViewById(R.id.usdIrrRate)
        moneyAmount = findViewById(R.id.moneyAmount)
        moneyResult = findViewById(R.id.moneyResult)
        noteDenomination = findViewById(R.id.noteDenomination)
        ocrResult = findViewById(R.id.ocrResult)
        priceImage = findViewById(R.id.priceImage)
    }

    private fun setupConversationButton() {
        val parent = recordVoiceButton.parent as LinearLayout
        conversationModeButton = Button(this).apply {
            text = "🔁 وضع المحادثة التلقائي: إيقاف"
            textSize = 15f
            setTextColor(Color.WHITE)
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(52)).apply {
                topMargin = dp(10)
            }
        }
        parent.addView(conversationModeButton, parent.indexOfChild(recordVoiceButton))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupLanguageSelectors() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, languages.map { it.label }).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        sourceLanguageSpinner.adapter = adapter
        targetLanguageSpinner.adapter = adapter
        sourceLanguageSpinner.setSelection(0)
        targetLanguageSpinner.setSelection(2)
    }

    private fun selectedSourceLanguage(): String = languages[sourceLanguageSpinner.selectedItemPosition.coerceIn(0, languages.lastIndex)].code
    private fun selectedTargetLanguage(): String = languages[targetLanguageSpinner.selectedItemPosition.coerceIn(0, languages.lastIndex)].code

    private fun setupActions() {
        conversationModeButton.setOnClickListener { toggleConversationMode() }
        recordVoiceButton.setOnClickListener {
            currentRecordLanguage = selectedSourceLanguage()
            startVoice(RecordMode.TRANSLATE, currentRecordLanguage)
        }
        stopRecordButton.setOnClickListener { stopVoiceAndProcess() }
        translateButton.setOnClickListener { translateManual() }
        swapButton.setOnClickListener { swapLanguages() }
        speakButton.setOnClickListener {
            translatedText.text.toString().trim().takeIf { it.isNotEmpty() }?.let { speakLocal(it, lastSpeakLanguage) }
        }
        findViewById<Button>(R.id.saveRatesButton).setOnClickListener { saveRates() }
        findViewById<Button>(R.id.parseRialButton).setOnClickListener { calculateMoney("rial") }
        findViewById<Button>(R.id.parseTomanButton).setOnClickListener { calculateMoney("toman") }
        findViewById<Button>(R.id.parseUsdButton).setOnClickListener { calculateMoney("usd") }
        findViewById<Button>(R.id.parseIqdButton).setOnClickListener { calculateMoney("iqd") }
        findViewById<Button>(R.id.moneyVoiceFaButton).setOnClickListener { startVoice(RecordMode.MONEY, "fa") }
        findViewById<Button>(R.id.moneyVoiceArButton).setOnClickListener { startVoice(RecordMode.MONEY, "ar") }
        findViewById<Button>(R.id.moneyVoiceEnButton).setOnClickListener { startVoice(RecordMode.MONEY, "en") }
        findViewById<Button>(R.id.cameraButton).setOnClickListener { openCamera() }
        findViewById<Button>(R.id.galleryButton).setOnClickListener { openGallery() }
        findViewById<Button>(R.id.banknoteCountButton).setOnClickListener { calculateBanknoteCount() }
    }

    private fun toggleConversationMode() {
        conversationMode = !conversationMode
        pendingListenLanguage = null
        pendingSwapBeforeListen = false
        if (conversationMode) {
            conversationModeButton.text = "🟢 وضع المحادثة التلقائي: يعمل"
            conversationModeButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#059669"))
            understandingHint.text = "🟢 بعد نطق الترجمة سيبدّل التطبيق اللغات ويبدأ الاستماع للطرف الآخر تلقائيًا. اضغط إيقاف بعد كل جملة."
        } else {
            conversationModeButton.text = "🔁 وضع المحادثة التلقائي: إيقاف"
            conversationModeButton.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#475569"))
            understandingHint.text = "إذا لم يفهم الكلام بوضوح سيطلب منك إعادة النطق."
        }
    }

    private fun swapLanguageSelectorsOnly() {
        val s = sourceLanguageSpinner.selectedItemPosition
        val t = targetLanguageSpinner.selectedItemPosition
        sourceLanguageSpinner.setSelection(t)
        targetLanguageSpinner.setSelection(s)
    }

    private fun swapLanguages() {
        swapLanguageSelectorsOnly()
        val src = sourceText.text.toString()
        val dst = translatedText.text.toString()
        if (dst.isNotBlank()) {
            sourceText.setText(dst)
            translatedText.setText(src)
        }
    }

    private fun setupTts() {
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) {
                        if (utteranceId?.startsWith("conv_") == true) {
                            pendingListenLanguage = null
                            pendingSwapBeforeListen = false
                        }
                    }
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId?.startsWith("conv_") != true) return
                        val nextLanguage = pendingListenLanguage ?: return
                        val shouldSwap = pendingSwapBeforeListen
                        pendingListenLanguage = null
                        pendingSwapBeforeListen = false
                        runOnUiThread {
                            if (!conversationMode || isFinishing || isDestroyed) return@runOnUiThread
                            if (shouldSwap) swapLanguageSelectorsOnly()
                            currentRecordLanguage = nextLanguage
                            scope.launch {
                                delay(350)
                                if (conversationMode && recorder?.isRecording() != true) {
                                    startVoice(RecordMode.TRANSLATE, nextLanguage)
                                }
                            }
                        }
                    }
                })
            }
        }
    }

    private fun prepareOfflineEngines() {
        scope.launch {
            try {
                statusText.text = "جاري تجهيز نماذج الترجمة والصوت المحلية…"
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 2
                val modelFile = File(filesDir, "models/qwen.gguf")
                withContext(Dispatchers.IO) {
                    copyLargeAsset("models/qwen.gguf", modelFile, 5, 70)
                    OcrEngine.prepare(this@MainActivity)
                }
                progressBar.progress = 75
                statusText.text = "تحميل محرك الترجمة…"
                llamaModel = Llama.loadModel(
                    modelFile.absolutePath,
                    LlamaConfig(
                        contextSize = 1536,
                        threads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)),
                        temperature = 0.05f,
                        topP = 0.82f,
                        topK = 20,
                        seed = 7
                    )
                )
                progressBar.progress = 86
                statusText.text = "تحميل Whisper Small للتعرف على الكلام…"
                whisperModel = Whisper.loadModelFromAsset(this@MainActivity, "models/ggml-small.bin")
                progressBar.progress = 100
                modelsReady = true
                setModelControlsEnabled(true)
                statusText.text = "جاهز — عربي • English • فارسی — يعمل بدون إنترنت"
            } catch (e: Exception) {
                modelsReady = false
                setModelControlsEnabled(false)
                progressBar.visibility = View.GONE
                statusText.text = "تعذر تجهيز النموذج المحلي: ${e.message ?: e.javaClass.simpleName}. تحويل العملات اليدوي ما زال يعمل."
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
            conversationModeButton, recordVoiceButton, translateButton, swapButton, speakButton,
            findViewById<Button>(R.id.moneyVoiceFaButton), findViewById<Button>(R.id.moneyVoiceArButton),
            findViewById<Button>(R.id.moneyVoiceEnButton), findViewById<Button>(R.id.cameraButton),
            findViewById<Button>(R.id.galleryButton)
        ).forEach { it.isEnabled = enabled }
    }

    private fun startVoice(mode: RecordMode, language: String) {
        if (!modelsReady) return toast("انتظر حتى يكتمل تجهيز النماذج المحلية")
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO_PERMISSION)
            return toast("اسمح بالميكروفون ثم اضغط زر الكلام مرة أخرى")
        }
        if (recorder?.isRecording() == true) return
        recordMode = mode
        currentRecordLanguage = language
        val wav = File(cacheDir, "speech_${System.currentTimeMillis()}.wav")
        try {
            recorder = WavRecorder(wav).also { it.start() }
            stopRecordButton.isEnabled = true
            recordVoiceButton.isEnabled = false
            findViewById<Button>(R.id.moneyVoiceFaButton).isEnabled = false
            findViewById<Button>(R.id.moneyVoiceArButton).isEnabled = false
            findViewById<Button>(R.id.moneyVoiceEnButton).isEnabled = false
            statusText.text = when (language) {
                "fa" -> "در حال شنیدن… بعد از پایان، توقف را بزنید."
                "en" -> "Listening… tap Stop when you finish."
                else -> "أسمعك الآن… اضغط إيقاف عند الانتهاء."
            }
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
            askToRepeat(currentRecordLanguage)
            return
        }
        scope.launch {
            transcribeAndProcess(file, mode, currentRecordLanguage)
            file.delete()
        }
    }

    private suspend fun transcribeAndProcess(audioFile: File, mode: RecordMode, language: String) {
        val model = whisperModel ?: return
        try {
            statusText.text = "تحويل الصوت إلى نص محليًا…"
            val result = Whisper.transcribe(
                model,
                audioFile.absolutePath,
                WhisperConfig(language = language, translate = false, threads = min(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1)))
            )
            val text = result.text.trim()
            if (isUnclearSpeech(text)) {
                askToRepeat(language)
                return
            }
            understandingHint.text = if (text.length < 6)
                "🟡 الكلام قصير؛ راجع الكلمة قبل الاعتماد عليها."
            else "🟢 تم فهم الكلام. الترجمة ستبدأ مباشرة."
            when (mode) {
                RecordMode.TRANSLATE -> {
                    sourceText.setText(text)
                    translateText(text, selectedSourceLanguage(), selectedTargetLanguage(), true)
                }
                RecordMode.MONEY -> {
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

    private fun isUnclearSpeech(text: String): Boolean {
        if (text.length < 2) return true
        val lower = text.lowercase()
        if (lower.contains("[blank") || lower.contains("[noise") || lower.contains("[silence") || lower == "." || lower == "...") return true
        val meaningful = text.count { it.isLetterOrDigit() }
        if (meaningful < 2) return true
        val words = text.split(Regex("\\s+")).filter { it.any(Char::isLetterOrDigit) }
        if (words.isEmpty()) return true
        return false
    }

    private fun askToRepeat(language: String) {
        val message = when (language) {
            "fa" -> "واضح نشنیدم. لطفاً دوباره آرام و واضح صحبت کنید."
            "en" -> "I didn't understand clearly. Please say it again slowly and clearly."
            else -> "ما فهمت الكلام بوضوح. رجاءً أعد الكلام ببطء ووضوح."
        }
        understandingHint.text = "🔴 $message"
        statusText.text = message
        if (conversationMode) {
            speakLocal(message, language, nextListenLanguage = language, swapBeforeListen = false)
        } else {
            speakLocal(message, language)
        }
    }

    private fun translateManual() {
        val text = sourceText.text.toString().trim()
        if (text.isEmpty()) return toast("اكتب أو سجل الكلام أولًا")
        scope.launch { translateText(text, selectedSourceLanguage(), selectedTargetLanguage(), false) }
    }

    private suspend fun translateText(text: String, sourceLang: String, targetLang: String, autoSpeak: Boolean) {
        if (sourceLang == targetLang) {
            translatedText.setText(text)
            lastSpeakLanguage = targetLang
            if (autoSpeak) speakLocal(text, targetLang)
            return
        }
        val model = llamaModel ?: return
        try {
            statusText.text = "جاري الترجمة على الهاتف…"
            val result = Llama.complete(model, text, systemPrompt = translationPrompt(sourceLang, targetLang), maxTokens = 220)
            val cleaned = result.text.trim()
                .removePrefix("Translation:")
                .removePrefix("الترجمة:")
                .removePrefix("ترجمه:")
                .trim().trim('"', '\'', '«', '»')
            if (cleaned.isBlank()) {
                translatedText.setText("لم أستطع تكوين ترجمة واضحة. صحح النص أو أعد نطق الجملة.")
                understandingHint.text = "🟡 الترجمة غير مؤكدة."
                return
            }
            translatedText.setText(cleaned)
            lastSpeakLanguage = targetLang
            statusText.text = "تمت الترجمة محليًا."
            if (autoSpeak) {
                if (conversationMode) speakLocal(cleaned, targetLang, nextListenLanguage = targetLang, swapBeforeListen = true)
                else speakLocal(cleaned, targetLang)
            }
        } catch (e: Exception) {
            statusText.text = "خطأ في الترجمة المحلية: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun translationPrompt(source: String, target: String): String {
        val sourceName = when (source) { "ar" -> "Arabic (including Iraqi dialect)"; "fa" -> "Persian"; else -> "English" }
        val targetName = when (target) { "ar" -> "clear natural Arabic, preferring understandable Iraqi wording when conversational"; "fa" -> "natural everyday Persian"; else -> "natural English" }
        return "You are a high-accuracy offline translator. Translate from $sourceName to $targetName. Preserve names, numbers, money values, units and meaning. Understand Iraqi colloquial Arabic words such as ماكو, شكد, وين, هسه, اريد, خوش. Prefer the intended conversational meaning over a word-for-word translation. Do not explain, summarize or add information. Output ONLY the translation."
    }

    private fun speakLocal(text: String, language: String, nextListenLanguage: String? = null, swapBeforeListen: Boolean = false) {
        if (!ttsReady) {
            pendingListenLanguage = null
            pendingSwapBeforeListen = false
            return
        }
        val engine = tts ?: return
        val locale = when (language) {
            "fa" -> Locale("fa", "IR")
            "en" -> Locale.US
            else -> Locale("ar", "IQ")
        }
        engine.language = locale
        val offlineVoice = engine.voices?.firstOrNull { it.locale.language == locale.language && !it.isNetworkConnectionRequired }
        if (offlineVoice == null) {
            statusText.text = "لا يوجد صوت أوفلاين مثبت لهذه اللغة على الهاتف. الترجمة النصية تعمل."
            pendingListenLanguage = null
            pendingSwapBeforeListen = false
            return
        }
        engine.voice = offlineVoice
        val utteranceId = if (conversationMode && nextListenLanguage != null) {
            pendingListenLanguage = nextListenLanguage
            pendingSwapBeforeListen = swapBeforeListen
            "conv_${System.currentTimeMillis()}"
        } else {
            "offline_translation_${System.currentTimeMillis()}"
        }
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun loadSavedRates() {
        usdIqdRate.setText(prefs.getString("usd_iqd", "") ?: "")
        usdIrrRate.setText(prefs.getString("usd_irr", "") ?: "")
    }

    private fun saveRates() {
        val iqd = parseRate(usdIqdRate)
        val irr = parseRate(usdIrrRate)
        if (iqd == null || irr == null || iqd <= 0 || irr <= 0) return toast("أدخل سعر الدولار بالدينار والريال بشكل صحيح")
        prefs.edit().putString("usd_iqd", iqd.toString()).putString("usd_irr", irr.toString()).apply()
        toast("تم حفظ سعر الصرف محليًا")
        calculateMoney(MoneyUtils.detectCurrency(moneyAmount.text.toString()))
    }

    private fun parseRate(field: EditText): Double? = MoneyUtils.normalizeDigits(field.text.toString()).replace(",", "").toDoubleOrNull()

    private fun getRates(): MoneyUtils.Rates? {
        val iqd = parseRate(usdIqdRate)
        val irr = parseRate(usdIrrRate)
        return if (iqd != null && irr != null && iqd > 0 && irr > 0) MoneyUtils.Rates(iqd, irr) else null
    }

    private fun calculateMoney(forcedCurrency: String) {
        val raw = moneyAmount.text.toString().trim()
        val amount = MoneyUtils.parseAmount(raw)
        if (amount == null) {
            moneyResult.text = "لم أفهم المبلغ. اكتبه بالأرقام مثل: 25000000 ريال."
            return
        }
        val rates = getRates()
        if (rates == null) {
            moneyResult.text = "أدخل سعر الصرف اليدوي واحفظه أولًا.\nالمبلغ المقروء: ${DecimalFormat("#,##0").format(amount)}"
            return
        }
        val currency = if (MoneyUtils.hasExplicitCurrency(raw)) MoneyUtils.detectCurrency(raw) else forcedCurrency
        val conversion = MoneyUtils.convert(amount.toDouble(), currency, rates)
        moneyResult.text = if (conversion == null) "تعذر الحساب. تحقق من أسعار الصرف." else
            "المبلغ: ${DecimalFormat("#,##0").format(amount)} ${currencyLabel(currency)}\n\n${MoneyUtils.formatConversion(conversion)}\n\nسعر الصرف اليدوي المستخدم:\n1 USD = ${DecimalFormat("#,##0.##").format(rates.usdToIqd)} IQD\n1 USD = ${DecimalFormat("#,##0.##").format(rates.usdToIrr)} IRR"
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
        if (intent.resolveActivity(packageManager) == null) return toast("لا يوجد تطبيق كاميرا متاح")
        try {
            val file = File(cacheDir, "price_capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            cameraImageFile = file
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            toast("تعذر فتح الكاميرا: ${e.message ?: "خطأ غير معروف"}")
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
        startActivityForResult(Intent.createChooser(intent, "اختر صورة السعر"), REQ_GALLERY)
    }

    @Deprecated("Compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            REQ_CAMERA -> {
                val file = cameraImageFile
                try {
                    val bitmap = if (file != null && file.exists() && file.length() > 0L) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        data?.extras?.get("data") as? Bitmap
                    }
                    if (bitmap != null) {
                        val scaled = scaleForOcr(bitmap)
                        if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                        analyzePriceImage(scaled)
                    } else toast("تعذر قراءة صورة الكاميرا")
                } finally {
                    file?.delete()
                    cameraImageFile = null
                }
            }
            REQ_GALLERY -> {
                val uri = data?.data ?: return
                try {
                    contentResolver.openInputStream(uri).use { input ->
                        val bitmap = BitmapFactory.decodeStream(input)
                        if (bitmap != null) {
                            val scaled = scaleForOcr(bitmap)
                            if (scaled !== bitmap && !bitmap.isRecycled) bitmap.recycle()
                            analyzePriceImage(scaled)
                        }
                    }
                } catch (e: Exception) {
                    ocrResult.text = "تعذر فتح الصورة: ${e.message}"
                }
            }
        }
    }

    private fun scaleForOcr(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 2400) return bitmap
        val ratio = 2400f / maxSide.toFloat()
        return Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).toInt(), (bitmap.height * ratio).toInt(), true)
    }

    private fun analyzePriceImage(bitmap: Bitmap) {
        priceImage.setImageBitmap(bitmap)
        ocrResult.text = "جاري قراءة السعر من الصورة محليًا…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.Default) { OcrEngine.recognize(this@MainActivity, bitmap) }
                val amount = MoneyUtils.parsePriceAmount(result.text)
                if (amount == null) {
                    ocrResult.text = "لم أتعرف على سعر واضح.\nثقة القراءة: ${result.confidence}%\nالنص المقروء:\n${result.text.ifBlank { "لا يوجد نص واضح" }}\n\nقرب الكاميرا من السعر واجعله مستقيمًا وواضح الإضاءة ثم حاول مرة أخرى."
                    return@launch
                }
                val currency = MoneyUtils.detectCurrency(result.text, defaultRialForBanknote = true)
                moneyAmount.setText("$amount ${currencyLabel(currency)}")
                calculateMoney(currency)
                val targetRial = targetRialFromInput()
                val noteRial = parseNoteRial()
                val countMessage = if (targetRial != null && noteRial != null && noteRial > 0) MoneyUtils.banknoteCountRial(targetRial, noteRial)
                    else "أدخل فئة الورقة التي معك بالريال حتى أحسب عدد الأوراق المطلوبة."
                ocrResult.text = "السعر المقروء: ${DecimalFormat("#,##0").format(amount)} ${currencyLabel(currency)}\nثقة OCR: ${result.confidence}%\n\n$countMessage\n\nالنص المقروء:\n${result.text}"
            } catch (e: Exception) {
                ocrResult.text = "تعذر تحليل الصورة: ${e.message ?: e.javaClass.simpleName}\nيمكنك كتابة السعر يدويًا."
            }
        }
    }

    private fun parseNoteRial(): Long? = MoneyUtils.normalizeDigits(noteDenomination.text.toString()).replace(",", "").toLongOrNull()

    private fun calculateBanknoteCount() {
        val noteRial = parseNoteRial()
        val targetRial = targetRialFromInput()
        ocrResult.text = when {
            noteRial == null || noteRial <= 0 -> "أدخل فئة الورقة بالريال الإيراني أولًا."
            targetRial == null || targetRial <= 0 -> "أدخل السعر أو صوّره أولًا."
            else -> MoneyUtils.banknoteCountRial(targetRial, noteRial)
        }
    }

    private fun targetRialFromInput(): Long? {
        val raw = moneyAmount.text.toString()
        val amount = MoneyUtils.parseAmount(raw) ?: return null
        val rates = getRates()
        return when (MoneyUtils.detectCurrency(raw, defaultRialForBanknote = true)) {
            "rial" -> amount
            "toman" -> amount * 10L
            "usd" -> rates?.let { (amount * it.usdToIrr).toLong() }
            "iqd" -> rates?.let { ((amount / it.usdToIqd) * it.usdToIrr).toLong() }
            else -> amount
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        pendingListenLanguage = null
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
