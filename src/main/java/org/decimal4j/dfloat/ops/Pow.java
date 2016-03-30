/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 decimal4j (tools4j), Marco Terzer
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package org.decimal4j.dfloat.ops;

import org.decimal4j.dfloat.attribute.Attributes;
import org.decimal4j.dfloat.attribute.RoundingDirection;
import org.decimal4j.dfloat.dpd.Dpd;
import org.decimal4j.dfloat.dpd.Rem;
import org.decimal4j.dfloat.dpd.Shift;
import org.decimal4j.dfloat.encode.Decimal64;
import org.decimal4j.dfloat.signal.Signal;

public final class Pow {

    private static final String SCALE_10 = "scale10";
    private static final String LOG_10 = "log10";

    private Pow() {
        throw new RuntimeException("No Pow for you!");
    }

    public static long scale10(final long x, final int n) {
        return scale10(x, n, Attributes.DEFAULT);
    }
    public static long scale10(final long x, final int n, final Attributes attributes) {
        if (n != 0 & Decimal64.isFinite(x)) {
            final int exp = Decimal64.getExponent(x);
            if (Decimal64.isZero(x)) {
                //zero with preferred quantum, capped at min/max possible
                final int e = Math.min(Math.max(exp + n, Decimal64.MIN_EXPONENT_NOMINAL), Decimal64.MAX_EXPONENT_NOMINAL);
                return Decimal64.zero(x, e);
            }
            final int e = exp + n;
            final int msd = Decimal64.getCombinationMSD(x);
            if (Decimal64.MIN_EXPONENT_NOMINAL <= e & e <= Decimal64.MAX_EXPONENT_NOMINAL) {
                return Decimal64.encode(x, e, msd, Dpd.canonicalize(x));
            }
            if (e < Decimal64.MIN_EXPONENT_NOMINAL) {
                final int shift = Decimal64.MIN_EXPONENT_NOMINAL - e;
                final long shifted = Shift.shiftRight(msd, x, shift);
                final Remainder rem = Rem.remainderOfPow10(msd, x, shift);
                if (rem == Remainder.ZERO) {
                    //still exact when shifting right
                    return Decimal64.encode(x, e + shift, 0, shifted);
                }
                //inexact after shifting right
                final RoundingDirection roundingDirection = attributes.getDecimalRoundingDirection();
                long rdpd = shifted;
                int rmsd = 0;
                if (roundingDirection.isRoundingIncrementPossible(x)) {
                    final int mod = Rem.mod10(shifted);
                    final int inc = roundingDirection.getRoundingIncrement(x, mod, rem);
                    if (inc != 0) {
                        rdpd = Dpd.inc(shifted);
                        rmsd = (int) (rdpd >>> 50);
                    }
                }
                final long result = Decimal64.encode(x, Decimal64.MIN_EXPONENT_NOMINAL, rmsd, rdpd);
                return Signal.inexact(SCALE_10, x, n, result, attributes);
            }
            //e > Decimal64.MAX_EXPONENT_NOMINAL
            if (msd == 0) {
                final int nlz = 1 + Dpd.numberOfLeadingZeros(x);
                if (e - nlz <= Decimal64.MAX_EXPONENT_NOMINAL) {
                    //exponent fits when left shifting mantissa
                    final int shift = e - Decimal64.MAX_EXPONENT_NOMINAL;
                    final long dpd = Shift.shiftLeft(x, shift);
                    final int smsd = (int)(dpd >>> 50);
                    return Decimal64.encode(x, e - shift, smsd, dpd);
                }
            }
            //exponent overflow
            final long result = attributes.getDecimalRoundingDirection().roundOverflow(x);
            return Signal.overflow(SCALE_10, x, n, result, attributes);
        }
        //n == 0 or x == Nan of Inf
        return Decimal64.canonicalize(x);
    }

    public static int log10(final long x) {
        return log10(x, Attributes.DEFAULT);
    }
    public static int log10(final long x, final Attributes attributes) {
        if (Decimal64.isZero(x)) {
            return (int)Signal.divisionByZero(LOG_10, x, 0, Integer.MIN_VALUE, attributes);
        }
        if (Decimal64.isFinite(x)) {
            final int exp = Decimal64.getExponent(x);
            final int msd = Decimal64.getCombinationMSD(x);
            if (msd == 0) {
                final int nlz = 1 + Dpd.numberOfLeadingZeros(x);
                return exp + Decimal64.MAX_PRECISION - (nlz + 1);
            }
            return exp + Decimal64.MAX_PRECISION - 1;
        }
        //Infinity or NaN
        return (int)Signal.invalidOperation(LOG_10, x, 0, Integer.MAX_VALUE, attributes);
    }
}