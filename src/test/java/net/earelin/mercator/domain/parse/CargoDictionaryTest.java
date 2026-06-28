package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class CargoDictionaryTest {

    @Test
    void maps_the_documented_role_labels_to_the_baseline_codes() {
        assertThat(CargoDictionary.lookup("Adm. Unico")).contains(Cargo.ADM_UNICO);
        assertThat(CargoDictionary.lookup("Adm. Solid.")).contains(Cargo.ADM_SOLIDARIO);
        assertThat(CargoDictionary.lookup("ADM.SOLIDAR.")).contains(Cargo.ADM_SOLIDARIO);
        assertThat(CargoDictionary.lookup("Cons.Del.Sol")).contains(Cargo.CONS_DEL_SOL);
        assertThat(CargoDictionary.lookup("Con.Delegado")).contains(Cargo.CON_DELEGADO);
        assertThat(CargoDictionary.lookup("Apo.Sol.")).contains(Cargo.APO_SOL);
        assertThat(CargoDictionary.lookup("LiqUnico")).contains(Cargo.LIQ_UNICO);
        assertThat(CargoDictionary.lookup("Aud.C.Con.")).contains(Cargo.AUD_C_CON);
        assertThat(CargoDictionary.lookup("Socio único")).contains(Cargo.SOCIO_UNICO);
    }

    @Test
    void maps_spacing_variants_of_the_same_cargo_to_one_code() {
        assertThat(CargoDictionary.lookup("Adm. Unico")).contains(Cargo.ADM_UNICO);
        assertThat(CargoDictionary.lookup("ADM.UNICO")).contains(Cargo.ADM_UNICO);
        assertThat(CargoDictionary.lookup("Admin.Unico")).contains(Cargo.ADM_UNICO);
    }

    @Test
    void trims_surrounding_whitespace_before_lookup() {
        assertThat(CargoDictionary.lookup("  Presidente  ")).contains(Cargo.PRESIDENTE);
    }

    @Test
    void returns_empty_for_an_unknown_or_null_label() {
        assertThat(CargoDictionary.lookup("Jefe Supremo")).isEmpty();
        assertThat(CargoDictionary.lookup(null)).isEmpty();
        assertThat(CargoDictionary.lookup("")).isEqualTo(Optional.empty());
    }
}
