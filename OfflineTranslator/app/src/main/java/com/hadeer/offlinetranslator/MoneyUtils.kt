package com.hadeer.offlinetranslator

import java.text.DecimalFormat

object MoneyUtils {
    private fun canonWord(s: String): String = s.replace('ي', 'ی').replace('ك', 'ک').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')

    data class Rates(val usdToIqd: Double, val usdToToman: Double)
    data class Conversion(val toman: Double, val rial: Double, val usd: Double, val iqd: Double)

    private val fa = mapOf(
        "صفر" to 0L, "یک" to 1L, "يه" to 1L, "دو" to 2L, "سه" to 3L, "چهار" to 4L,
        "پنج" to 5L, "شش" to 6L, "هفت" to 7L, "هشت" to 8L, "نه" to 9L,
        "ده" to 10L, "یازده" to 11L, "يازده" to 11L, "دوازده" to 12L, "سیزده" to 13L,
        "چهارده" to 14L, "پانزده" to 15L, "شانزده" to 16L, "هفده" to 17L, "هجده" to 18L, "نوزده" to 19L,
        "بیست" to 20L, "بيست" to 20L, "سی" to 30L, "سي" to 30L, "چهل" to 40L,
        "پنجاه" to 50L, "شصت" to 60L, "هفتاد" to 70L, "هشتاد" to 80L, "نود" to 90L,
        "صد" to 100L, "یکصد" to 100L, "دویست" to 200L, "سيصد" to 300L, "سیصد" to 300L,
        "چهارصد" to 400L, "پانصد" to 500L, "ششصد" to 600L, "هفتصد" to 700L, "هشتصد" to 800L, "نهصد" to 900L
    ).mapKeys { canonWord(it.key) }

    private val ar = mapOf(
        "صفر" to 0L, "واحد" to 1L, "وحد" to 1L, "واحدة" to 1L, "واحده" to 1L, "اثنين" to 2L,
        "اثنان" to 2L, "اثنينه" to 2L, "ثنين" to 2L, "ثلاثة" to 3L, "ثلاثه" to 3L, "اربع" to 4L,
        "أربع" to 4L, "اربعة" to 4L, "أربعة" to 4L, "خمسة" to 5L, "خمسه" to 5L, "ستة" to 6L,
        "سته" to 6L, "سبعة" to 7L, "سبعه" to 7L, "ثمانية" to 8L, "ثمانيه" to 8L, "تسعة" to 9L,
        "تسعه" to 9L, "عشرة" to 10L, "عشره" to 10L, "احدعش" to 11L, "احدعشر" to 11L,
        "اثنعش" to 12L, "اثناعشر" to 12L, "ثلاثتعش" to 13L, "اربعتعش" to 14L, "خمستعش" to 15L,
        "ستعش" to 16L, "سبعتعش" to 17L, "ثمنتعش" to 18L, "تسعتعش" to 19L,
        "عشرين" to 20L, "عشرون" to 20L, "ثلاثين" to 30L, "ثلاثون" to 30L,
        "اربعين" to 40L, "أربعين" to 40L, "خمسين" to 50L, "ستين" to 60L, "سبعين" to 70L,
        "ثمانين" to 80L, "تسعين" to 90L, "مية" to 100L, "مئة" to 100L, "مائه" to 100L,
        "مئتين" to 200L, "ميتين" to 200L, "ثلاثمية" to 300L, "ثلاثمئة" to 300L, "اربعمية" to 400L,
        "أربعمية" to 400L, "خمسمية" to 500L, "ستمية" to 600L, "سبعمية" to 700L,
        "ثمانمية" to 800L, "تسعمية" to 900L
    ).mapKeys { canonWord(it.key) }

    private val scales = mapOf(
        "هزار" to 1_000L, "هزارتا" to 1_000L,
        "میلیون" to 1_000_000L, "ميليون" to 1_000_000L,
        "میلیارد" to 1_000_000_000L, "ميليارد" to 1_000_000_000L,
        "الف" to 1_000L, "ألف" to 1_000L, "آلاف" to 1_000L, "الاف" to 1_000L,
        "مليون" to 1_000_000L, "ملايين" to 1_000_000L,
        "مليار" to 1_000_000_000L, "مليارات" to 1_000_000_000L
    ).mapKeys { canonWord(it.key) }

    fun normalizeDigits(input: String): String {
        val out = StringBuilder(input.length)
        input.forEach { c ->
            out.append(
                when (c) {
                    '٠', '۰' -> '0'
                    '١', '۱' -> '1'
                    '٢', '۲' -> '2'
                    '٣', '۳' -> '3'
                    '٤', '۴' -> '4'
                    '٥', '۵' -> '5'
                    '٦', '۶' -> '6'
                    '٧', '۷' -> '7'
                    '٨', '۸' -> '8'
                    '٩', '۹' -> '9'
                    '٬', '،' -> ','
                    else -> c
                }
            )
        }
        return out.toString()
    }

    fun parseAmount(input: String): Long? {
        val normalized = normalizeDigits(input)
        val digitMatches = Regex("(?<!\\d)\\d[\\d,._ ]{0,18}").findAll(normalized)
            .mapNotNull { m ->
                val clean = m.value.replace(Regex("[,._ ]"), "")
                clean.toLongOrNull()
            }.toList()
        if (digitMatches.isNotEmpty()) return digitMatches.maxOrNull()
        return parseWords(normalized)
    }

    private fun parseWords(input: String): Long? {
        val cleaned = canonWord(input).lowercase().replace(Regex("[\\p{Punct}؟،؛]+"), " ")
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() && it != "و" }
        var total = 0L
        var current = 0L
        var recognized = 0
        for (token in tokens) {
            val scale = scales[token]
            if (scale != null) {
                val base = if (current == 0L) 1L else current
                total += base * scale
                current = 0L
                recognized++
                continue
            }
            val value = fa[token] ?: ar[token]
            if (value != null) {
                current += value
                recognized++
            }
        }
        return if (recognized == 0) null else total + current
    }

    fun detectCurrency(text: String, defaultRialForBanknote: Boolean = false): String {
        val t = text.lowercase()
        return when {
            t.contains("تومان") || t.contains("تومن") -> "toman"
            t.contains("ریال") || t.contains("ريال") -> "rial"
            t.contains("دینار") || t.contains("دينار") || t.contains("عراقي") -> "iqd"
            t.contains("دلار") || t.contains("دولار") || t.contains("$") -> "usd"
            defaultRialForBanknote -> "rial"
            else -> "toman"
        }
    }

    fun convert(amount: Double, currency: String, rates: Rates): Conversion? {
        if (amount < 0 || rates.usdToIqd <= 0 || rates.usdToToman <= 0) return null
        val toman = when (currency) {
            "toman" -> amount
            "rial" -> amount / 10.0
            "usd" -> amount * rates.usdToToman
            "iqd" -> (amount / rates.usdToIqd) * rates.usdToToman
            else -> amount
        }
        val usd = toman / rates.usdToToman
        val iqd = usd * rates.usdToIqd
        return Conversion(toman, toman * 10.0, usd, iqd)
    }

    fun formatConversion(c: Conversion): String {
        val n0 = DecimalFormat("#,##0")
        val n2 = DecimalFormat("#,##0.00")
        return "${n0.format(c.toman)} تومان\n${n0.format(c.rial)} ريال إيراني\n${n0.format(c.iqd)} دينار عراقي\n${n2.format(c.usd)} دولار"
    }

    fun banknoteCount(targetToman: Long, noteRial: Long): String {
        if (targetToman <= 0 || noteRial <= 0) return "أدخل المبلغ المطلوب أولًا."
        val noteToman = noteRial / 10L
        if (noteToman <= 0) return "لم أستطع تحديد فئة الورقة."
        val full = targetToman / noteToman
        val remaining = targetToman % noteToman
        return if (remaining == 0L) {
            "تحتاج $full ورقة من فئة ${DecimalFormat("#,##0").format(noteRial)} ريال (${DecimalFormat("#,##0").format(noteToman)} تومان)."
        } else {
            val next = full + 1
            val over = next * noteToman - targetToman
            "يمكن دفع $full ورقة ويبقى ${DecimalFormat("#,##0").format(remaining)} تومان، أو $next ورقة ويكون الباقي لك ${DecimalFormat("#,##0").format(over)} تومان."
        }
    }

    fun likelyBanknoteDenomination(ocrText: String): Long? {
        val nums = Regex("\\d[\\d,._ ]{2,15}").findAll(normalizeDigits(ocrText))
            .mapNotNull { it.value.replace(Regex("[,._ ]"), "").toLongOrNull() }
            .filter { it in 1_000L..100_000_000L }.toList()
        if (nums.isEmpty()) return null
        val common = listOf(10_000L, 20_000L, 50_000L, 100_000L, 200_000L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L)
        return nums.minByOrNull { n -> common.minOf { kotlin.math.abs(it - n) } }
            ?.let { n -> common.minByOrNull { kotlin.math.abs(it - n) } ?: n }
    }
}
