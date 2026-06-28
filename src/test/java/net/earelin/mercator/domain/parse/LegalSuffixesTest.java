package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LegalSuffixesTest {

    @Test
    void detects_a_company_acting_as_administrator() {
        assertThat(LegalSuffixes.looksLikeCompany("DELOITTE SL")).isTrue();
        assertThat(LegalSuffixes.detect("DELOITTE SL")).contains("SL");
    }

    @Test
    void distinguishes_the_longer_suffixes_from_their_prefixes() {
        assertThat(LegalSuffixes.detect("FOO BAR SL")).contains("SL");
        assertThat(LegalSuffixes.detect("FOO BAR SLU")).contains("SLU");
        assertThat(LegalSuffixes.detect("FOO BAR SA")).contains("SA");
        assertThat(LegalSuffixes.detect("FOO BAR SAU")).contains("SAU");
    }

    @Test
    void treats_a_spelled_out_sociedad_as_a_company() {
        assertThat(LegalSuffixes.looksLikeCompany("GARNO INVESTMENTS SOCIEDAD ANONIMA")).isTrue();
    }

    @Test
    void treats_a_bare_person_name_as_not_a_company() {
        assertThat(LegalSuffixes.looksLikeCompany("RAMA SANCHEZ JOSE PEDRO")).isFalse();
        assertThat(LegalSuffixes.detect("RAMA SANCHEZ JOSE PEDRO")).isEmpty();
    }

    @Test
    void exposes_the_known_suffix_set() {
        assertThat(LegalSuffixes.forms()).contains("SL", "SA", "SLU", "SAU", "SCP", "AIE");
    }

    @Test
    void handles_null_safely() {
        assertThat(LegalSuffixes.looksLikeCompany(null)).isFalse();
        assertThat(LegalSuffixes.detect(null)).isEmpty();
    }
}
