package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProvinceDictionaryTest {

    @Test
    void resolves_province_names_to_their_seeded_code() {
        assertThat(ProvinceDictionary.codeForName("Pontevedra")).contains("36");
        assertThat(ProvinceDictionary.codeForName("Madrid")).contains("28");
        assertThat(ProvinceDictionary.codeForName("Barcelona")).contains("08");
    }

    @Test
    void resolves_regional_and_historical_spellings_to_one_code() {
        assertThat(ProvinceDictionary.codeForName("Girona")).contains("17");
        assertThat(ProvinceDictionary.codeForName("Gerona")).contains("17");
        assertThat(ProvinceDictionary.codeForName("A Coruña")).contains("15");
        assertThat(ProvinceDictionary.codeForName("La Coruña")).contains("15");
        assertThat(ProvinceDictionary.codeForName("Bizkaia")).contains("48");
        assertThat(ProvinceDictionary.codeForName("ARABA/ÁLAVA")).contains("01");
    }

    @Test
    void is_accent_and_case_insensitive() {
        assertThat(ProvinceDictionary.codeForName("PONTEVEDRA")).contains("36");
        assertThat(ProvinceDictionary.codeForName("almeria")).contains("04");
    }

    @Test
    void returns_empty_for_an_unknown_or_null_name() {
        assertThat(ProvinceDictionary.codeForName("Lisboa")).isEmpty();
        assertThat(ProvinceDictionary.codeForName(null)).isEmpty();
    }
}
