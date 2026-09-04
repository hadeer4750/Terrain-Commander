package com.hadeer.offlinetranslator

import java.text.DecimalFormat

object MoneyUtils {
    private fun canonWord(s: String): String = s.replace('ي', 'ی').replace('ك', 'ک').replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')

    data class Rates(val usdToIqd: Double, val usdToIrr: Double)
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
        "اثنان" to 2L, "ثنين" to 2L, "ثلاثة" to 3L, "ثلاثه" to 3L, "اربع" to 4L,
        "اربعة" to 4L, "خمسة" to 5L, "خمسه" to 5L, "ستة" to 6L, "سته" to 6L,
        "سبعة" to 7L, "سبعه" to 7L, "ثمانية" to 8L, "ثمانيه" to 8L, "تسعة" to 9L,
        "تسعه" to 9L, "عشرة" to 10L, "عشره" to 10L, "احدعش" to 11L, "احدعشر" to 11L,
        "اثنعش" to 12L, "اثناعشر" to 12L, "ثلاثتعش" to 13L, "اربعتعش" to 14L, "خمستعش" to 15L,
        "ستعش" to 16L, "سبعتعش" to 17L, "ثمنتعش" to 18L, "تسعتعش" to 19L,
        "عشرين" to 20L, "عشرون" to 20L, "ثلاثين" to 30L, "ثلاثون" to 30L,
        "اربعين" to 40L, "خمسين" to 50L, "ستين" to 60L, "سبعين" to 70L,
        "ثمانين" to 80L, "تسعين" to 90L, "مية" to 100L, "مئة" to 100L, "مائه" to 100L,
        "مئتين" to 200L, "ميتين" to 200L, "ثلاثمية" to 300L, "ثلاثمئة" to 300L, "اربعمية" to 400L,
        "خمسمية" to 500L, "ستمية" to 600L, "سبعمية" to 700L, "ثمانمية" to 800L, "تسعمية" to 900L
    ).mapKeys { canonWord(it.key) }

    private val en = mapOf(
        "zero" to 0L, "one" to 1L, "two" to 2L, "three" to 3L, "four" to 4L, "five" to 5L,
        "six" to 6L, "seven" to 7L, "eight" to 8L, "nine" to 9L, "ten" to 10L, "eleven" to 11L,
        "twelve" to 12L, "thirteen" to 13L, "fourteen" to 14L, "fifteen" to 15L, "sixteen" to 16L,
        "seventeen" to 17L, "eighteen" to 18L, "nineteen" to 19L, "twenty" to 20L, "thirty" to 30L,
        "forty" to 40L, "fifty" to 50L, "sixty" to 60L, "seventy" to 70L, "eighty" to 80L, "ninety" to 90L
    )

    private val scales = mapOf(
        "هزار" to 1_000L, "هزارتا" to 1_000L, "میلیون" to 1_000_000L, "ميليون" to 1_000_000L,
        "میلیارد" to 1_000_000_000L, "ميليارد" to 1_000_000_000L, "الف" to 1_000L, "آلاف" to 1_000L,
        "الاف" to 1_000L, "مليون" to 1_000_000L, "ملايين" to 1_000_000L, "مليار" to 1_000_000_000L,
        "hundred" to 100L, "thousand" to 1_000L, "million" to 1_000_000L, "billion" to 1_000_000_000L
    ).mapKeys { canonWord(it.key) }

    fun normalizeDigits(input: String): String {
        val out = StringBuilder(input.length)
        input.forEach { c ->
            out.append(when (c) {
                '٠', '۰' -> '0'; '١', '۱' -> '1'; '٢', '۲' -> '2'; '٣', '۳' -> '3'; '٤', '۴' -> '4'
                '٥', '۵' -> '5'; '٦', '۶' -> '6'; '٧', '۷' -> '7'; '٨', '۸' -> '8'; '٩', '۹' -> '9'
                '٬', '،' -> ','; else -> c
            })
        }
        return out.toString()
    }

    fun parseAmount(input: String): Long? {
        val normalized = normalizeDigits(input)
        val digitMatches = Regex("(?<!\\d)\\d[\\d,._ ]{0,18}").findAll(normalized)
            .mapNotNull { it.value.replace(Regex("[,._ ]"), "").toLongOrNull() }.toList()
        if (digitMatches.isNotEmpty()) return digitMatches.maxOrNull()
        return parseWords(normalized)
    }

    private fun parseWords(input: String): Long? {
        val cleaned = canonWord(input).lowercase().replace(Regex("[\\p{Punct}؟،؛]+"), " ")
        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() && it != "و" && it != "and" }
        var total = 0L
        var current = 0L
        var recognized = 0
        for (token in tokens) {
            val scale = scales[token]
            if (scale != null) {
                if (scale == 100L && current > 0L) current *= 100L
                else {
                    val base = if (current == 0L) 1L else current
                    total += base * scale
                    current = 0L
                }
                recognized++
                continue
            }
            val value = fa[token] ?: ar[token] ?: en[token]
            if (value != null) { current += value; recognized++ }
        }
        return if (recognized == 0) null else total + current
    }

    fun hasExplicitCurrency(text: String): Boolean {
        val t = text.lowercase()
        return listOf("تومان", "تومن", "ریال", "ريال", "irr", "دینار", "دينار", "iqd", "دلار", "دولار", "usd", "$").any { t.contains(it) }
    }

    fun detectCurrency(text: String, defaultRialForBanknote: Boolean = false): String {
        val t = text.lowercase()
        return when {
            t.contains("تومان") || t.contains("تومن") -> "toman"
            t.contains("ریال") || t.contains("ريال") || t.contains("irr") -> "rial"
            t.contains("دینار") || t.contains("دينار") || t.contains("عراقي") || t.contains("iqd") -> "iqd"
            t.contains("دلار") || t.contains("دولار") || t.contains("usd") || t.contains("$") -> "usd"
            defaultRialForBanknote -> "rial"
            else -> "rial"
        }
    }

    fun convert(amount: Double, currency: String, rates: Rates): Conversion? {
        if (amount < 0 || rates.usdToIqd <= 0 || rates.usdToIrr <= 0) return null
        val rial = when (currency) {
            "rial" -> amount
            "toman" -> amount * 10.0
            "usd" -> amount * rates.usdToIrr
            "iqd" -> (amount / rates.usdToIqd) * rates.usdToIrr
            else -> amount
        }
        val usd = rial / rates.usdToIrr
        val iqd = usd * rates.usdToIqd
        return Conversion(rial / 10.0, rial, usd, iqd)
    }

    fun formatConversion(c: Conversion): String {
        val n0 = DecimalFormat("#,##0")
        val n2 = DecimalFormat("#,##0.00")
        return "${n0.format(c.rial)} ريال إيراني\n${n0.format(c.toman)} تومان\n${n0.format(c.iqd)} دينار عراقي\n${n2.format(c.usd)} دولار"
    }

    fun banknoteCountRial(targetRial: Long, noteRial: Long): String {
        if (targetRial <= 0 || noteRial <= 0) return "أدخل السعر وفئة الورقة أولًا."
        val full = targetRial / noteRial
        val remaining = targetRial % noteRial
        val fmt = DecimalFormat("#,##0")
        return if (remaining == 0L) {
            "تحتاج $full ورقة من فئة ${fmt.format(noteRial)} ريال لتغطية المبلغ تمامًا."
        } else {
            val next = full + 1
            val over = next * noteRial - targetRial
            "تحتاج $next ورقة من فئة ${fmt.format(noteRial)} ريال لتغطية المبلغ. إذا دفعت $full ورقة يبقى ${fmt.format(remaining)} ريال، وإذا دفعت $next يكون الباقي لك ${fmt.format(over)} ريال."
        }
    }
}
