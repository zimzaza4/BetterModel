package kr.toxicity.model.api.nms;

/**
 * Horizontal alignment for a text display attached to a model bone.
 *
 * <p>Example:</p>
 * <pre>{@code
 * tracker.applyAtTextDisplay(BonePredicate.name("notice"), display ->
 *     display.alignment(ModelTextAlignment.CENTER)
 * );
 * }</pre>
 *
 * @since 3.3.0
 */
public enum ModelTextAlignment {
    /** Align text to the left edge. */
    LEFT,
    /** Center text. */
    CENTER,
    /** Align text to the right edge. */
    RIGHT
}
