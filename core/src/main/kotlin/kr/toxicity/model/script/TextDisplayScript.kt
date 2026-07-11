/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.script

import kr.toxicity.model.api.nms.ModelNametag
import kr.toxicity.model.api.nms.ModelTextAlignment
import kr.toxicity.model.api.platform.PlatformBillboard
import kr.toxicity.model.api.script.AnimationScript
import kr.toxicity.model.api.tracker.Tracker
import kr.toxicity.model.api.util.function.BonePredicate
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.format.TextColor

class TextDisplayScript(
    private val predicate: BonePredicate,
    private val value: String?,
    private val color: TextColor?,
    private val lineWidth: Int?,
    private val backgroundColor: Int?,
    private val textOpacity: Int?,
    private val shadowed: Boolean?,
    private val seeThrough: Boolean?,
    private val defaultBackground: Boolean?,
    private val alignment: ModelTextAlignment?,
    private val billboard: PlatformBillboard?,
    private val alwaysVisible: Boolean?
) : AnimationScript {

    override fun accept(tracker: Tracker) {
        tracker.applyAtTextDisplay(predicate) { display ->
            value?.let { text ->
                val component = parseMiniMessage(text)
                display.component(color?.let(component::color) ?: component)
            }
            lineWidth?.let(display::lineWidth)
            backgroundColor?.let(display::backgroundColor)
            textOpacity?.let(display::textOpacity)
            shadowed?.let(display::shadowed)
            seeThrough?.let(display::seeThrough)
            defaultBackground?.let(display::defaultBackground)
            alignment?.let(display::alignment)
            billboard?.let(display::billboard)
            alwaysVisible?.let(display::alwaysVisible)
        }
    }

    override fun isSync(): Boolean = true

    private fun parseMiniMessage(value: String): Component = runCatching {
        val type = Class.forName("net.kyori.adventure.text.minimessage.MiniMessage")
        val parser = type.getMethod("miniMessage").invoke(null)
        type.getMethod("deserialize", String::class.java).invoke(parser, value) as Component
    }.getOrElse { parseBasicMiniMessage(value) }

    private fun parseBasicMiniMessage(value: String): Component {
        val token = Regex("<(/)?([a-zA-Z_]+|#[0-9a-fA-F]{6})>")
        var result = Component.empty()
        var cursor = 0
        var color: TextColor? = null
        var decoration: TextDecoration? = null
        token.findAll(value).forEach { match ->
            if (match.range.first > cursor) {
                result = result.append(styled(value.substring(cursor, match.range.first), color, decoration))
            }
            val closing = match.groupValues[1].isNotEmpty()
            val name = match.groupValues[2]
            if (closing) {
                color = null
                decoration = null
            } else if (name.startsWith('#')) {
                color = TextColor.color(name.substring(1).toInt(16))
            } else {
                color = namedColor(name)
                decoration = when (name.lowercase()) {
                    "bold" -> TextDecoration.BOLD
                    "italic" -> TextDecoration.ITALIC
                    "underlined" -> TextDecoration.UNDERLINED
                    "strikethrough" -> TextDecoration.STRIKETHROUGH
                    "obfuscated" -> TextDecoration.OBFUSCATED
                    else -> decoration
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < value.length) result = result.append(styled(value.substring(cursor), color, decoration))
        return result
    }

    private fun styled(value: String, color: TextColor?, decoration: TextDecoration?): Component {
        var component = Component.text(value)
        color?.let { component = component.color(it) }
        decoration?.let { component = component.decorate(it) }
        return component
    }

    private fun namedColor(value: String): TextColor? = when (value.lowercase()) {
        "black" -> NamedTextColor.BLACK
        "dark_blue" -> NamedTextColor.DARK_BLUE
        "dark_green" -> NamedTextColor.DARK_GREEN
        "dark_aqua" -> NamedTextColor.DARK_AQUA
        "dark_red" -> NamedTextColor.DARK_RED
        "dark_purple" -> NamedTextColor.DARK_PURPLE
        "gold" -> NamedTextColor.GOLD
        "gray" -> NamedTextColor.GRAY
        "dark_gray" -> NamedTextColor.DARK_GRAY
        "blue" -> NamedTextColor.BLUE
        "green" -> NamedTextColor.GREEN
        "aqua" -> NamedTextColor.AQUA
        "red" -> NamedTextColor.RED
        "light_purple" -> NamedTextColor.LIGHT_PURPLE
        "yellow" -> NamedTextColor.YELLOW
        "white" -> NamedTextColor.WHITE
        else -> null
    }
}
