package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CargoDictionaryTest {

    @Test
    void maps_the_documented_role_labels_to_the_baseline_codes() {
        assertThat(CargoDictionary.lookup("Adm. Unico")).contains(Cargo.ADM_UNICO);
        assertThat(CargoDictionary.lookup("Adm. Solid.")).contains(Cargo.ADM_SOLIDARIO);
        assertThat(CargoDictionary.lookup("Cons.Del.Sol")).contains(Cargo.CONS_DEL_SOL);
        assertThat(CargoDictionary.lookup("Apo.Sol.")).contains(Cargo.APO_SOL);
        assertThat(CargoDictionary.lookup("LiqUnico")).contains(Cargo.LIQ_UNICO);
        assertThat(CargoDictionary.lookup("Aud.C.Con.")).contains(Cargo.AUD_C_CON);
        assertThat(CargoDictionary.lookup("Socio único")).contains(Cargo.SOCIO_UNICO);
    }

    @Test
    void matches_case_insensitively() {
        assertThat(CargoDictionary.lookup("Presidente")).contains(Cargo.PRESIDENTE);
        assertThat(CargoDictionary.lookup("PRESIDENTE")).contains(Cargo.PRESIDENTE);
        assertThat(CargoDictionary.lookup("presidente")).contains(Cargo.PRESIDENTE);
        assertThat(CargoDictionary.lookup("  ADM.UNICO  ")).contains(Cargo.ADM_UNICO);
    }

    @Test
    void generalises_numbered_ordinals_including_unseen_ones() {
        assertThat(CargoDictionary.lookup("Vocal")).contains(Cargo.VOCAL);
        assertThat(CargoDictionary.lookup("Vocal 1")).contains(Cargo.VOCAL);
        assertThat(CargoDictionary.lookup("Vocal 2")).contains(Cargo.VOCAL);
        assertThat(CargoDictionary.lookup("Vocal 3")).contains(Cargo.VOCAL);
    }

    @Test
    void falls_back_to_the_head_word_for_an_unmatched_label() {
        assertThat(CargoDictionary.lookup("Vocal Comisión Z")).contains(Cargo.VOCAL);
        assertThat(CargoDictionary.lookup("Presidente del órgano X")).contains(Cargo.PRESIDENTE);
        assertThat(CargoDictionary.lookup("Auditor de no sé qué")).contains(Cargo.AUDITOR);
    }

    @Test
    void keeps_worded_ordinals_distinct_rather_than_collapsing_them() {
        assertThat(CargoDictionary.lookup("Vicepr.1")).isPresent();
        assertThat(CargoDictionary.lookup("Vicepr.1"))
            .isNotEqualTo(CargoDictionary.lookup("Vicepr.2"));
    }

    @Test
    void returns_empty_when_neither_a_rule_nor_the_head_word_matches() {
        assertThat(CargoDictionary.lookup("Jefe Supremo")).isEmpty();
        assertThat(CargoDictionary.lookup(null)).isEmpty();
        assertThat(CargoDictionary.lookup("")).isEmpty();
    }
}
