/*
 * This source file is part of BetterModel.
 * Copyright (c) 2026 toxicity188
 * Licensed under the MIT License.
 * See LICENSE.md file for full license text.
 */

package kr.toxicity.model.api.util;

import kr.toxicity.model.api.data.Float3;
import org.junit.jupiter.api.Test;

import java.util.List;

import static java.lang.Math.abs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MathUtilTest {

    private static final float EULER_EPSILON = 1E-4F;
    private static final float QUATERNION_EPSILON = 1E-5F;

    @Test
    void quarterTurnSingularitiesPreserveBothRotationAxes() {
        var cases = List.of(
            new RotationCase(new Float3(-90F, 0F, -90F), new Float3(0F, 90F, -90F)),
            new RotationCase(new Float3(-90F, 0F, 90F), new Float3(0F, -90F, 90F)),
            new RotationCase(new Float3(90F, 0F, -90F), new Float3(0F, -90F, -90F)),
            new RotationCase(new Float3(90F, 0F, 90F), new Float3(0F, 90F, 90F))
        );

        for (var testCase : cases) {
            var source = testCase.source();
            var converted = source.convertToMinecraftDegree();

            assertEulerEquals(testCase.expected(), converted, source);
            assertEquivalent(source, converted);
        }
    }

    @Test
    void nonSingularConversionsRemainEquivalent() {
        var rotations = List.of(
            new Float3(-90F, 0F, -22.5F),
            new Float3(-90F, 0F, 22.5F),
            new Float3(90F, 0F, -22.5F),
            new Float3(90F, 0F, 22.5F),
            new Float3(90.01F, 0F, 90F)
        );

        for (var source : rotations) {
            assertEquivalent(source, source.convertToMinecraftDegree());
        }
    }

    private static void assertEulerEquals(Float3 expected, Float3 actual, Float3 source) {
        assertEquals(expected.x(), actual.x(), EULER_EPSILON, "X conversion for " + source);
        assertEquals(expected.y(), actual.y(), EULER_EPSILON, "Y conversion for " + source);
        assertEquals(expected.z(), actual.z(), EULER_EPSILON, "Z conversion for " + source);
    }

    private static void assertEquivalent(Float3 source, Float3 converted) {
        var expected = Quaternion.zRotation(source.z())
            .multiply(Quaternion.yRotation(source.y()))
            .multiply(Quaternion.xRotation(source.x()));
        var actual = Quaternion.xRotation(converted.x())
            .multiply(Quaternion.yRotation(converted.y()))
            .multiply(Quaternion.zRotation(converted.z()));
        var dot = abs(expected.dot(actual));
        assertTrue(dot > 1F - QUATERNION_EPSILON, "Quaternion conversion for " + source + " has dot product " + dot);
    }

    private record RotationCase(Float3 source, Float3 expected) {}

    private record Quaternion(double x, double y, double z, double w) {

        private static Quaternion xRotation(float degrees) {
            var radians = Math.toRadians(degrees) / 2D;
            return new Quaternion(Math.sin(radians), 0D, 0D, Math.cos(radians));
        }

        private static Quaternion yRotation(float degrees) {
            var radians = Math.toRadians(degrees) / 2D;
            return new Quaternion(0D, Math.sin(radians), 0D, Math.cos(radians));
        }

        private static Quaternion zRotation(float degrees) {
            var radians = Math.toRadians(degrees) / 2D;
            return new Quaternion(0D, 0D, Math.sin(radians), Math.cos(radians));
        }

        private Quaternion multiply(Quaternion other) {
            return new Quaternion(
                w * other.x + x * other.w + y * other.z - z * other.y,
                w * other.y - x * other.z + y * other.w + z * other.x,
                w * other.z + x * other.y - y * other.x + z * other.w,
                w * other.w - x * other.x - y * other.y - z * other.z
            );
        }

        private double dot(Quaternion other) {
            return x * other.x + y * other.y + z * other.z + w * other.w;
        }
    }
}
