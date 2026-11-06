package io.github.idymian.geops.coupons;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import io.github.idymian.geops.coupons.domain.CouponCodes;
import org.junit.jupiter.api.Test;

class CouponCodesTest {

    @Test
    void codesAvoidLookAlikeCharacters() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String code = CouponCodes.generate();
            assertThat(code).hasSize(CouponCodes.LENGTH).matches("[2-9A-HJKMNP-Z]+");
            codes.add(code);
        }
        assertThat(codes).hasSize(1_000);
    }

    @Test
    void whatTheCashierTypesIsNormalized() {
        assertThat(CouponCodes.normalize(" k7qm-4xpa ")).isEqualTo("K7QM4XPA");
        assertThat(CouponCodes.normalize("K7QM 4XPA")).isEqualTo("K7QM4XPA");
    }
}
