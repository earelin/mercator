package net.earelin.mercator.domain.parse;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ActDictionaryTest {

    @Test
    void maps_catalogued_keywords_to_their_act_type() {
        assertThat(ActDictionary.lookup("Constitución")).contains(ActType.CONSTITUCION);
        assertThat(ActDictionary.lookup("Nombramientos")).contains(ActType.NOMBRAMIENTO);
        assertThat(ActDictionary.lookup("Ceses/Dimisiones")).contains(ActType.CESE);
        assertThat(ActDictionary.lookup("Cambio de domicilio social")).contains(ActType.CAMBIO_DOMICILIO);
        assertThat(ActDictionary.lookup("Fusión por absorción")).contains(ActType.FUSION);
        assertThat(ActDictionary.lookup("Fe de erratas")).contains(ActType.FE_ERRATAS);
    }

    @Test
    void maps_both_unipersonalidad_keywords_to_one_type() {
        assertThat(ActDictionary.lookup("Declaración de unipersonalidad")).contains(ActType.UNIPERSONALIDAD);
        assertThat(ActDictionary.lookup("Sociedad unipersonal")).contains(ActType.UNIPERSONALIDAD);
    }

    @Test
    void returns_empty_for_an_uncatalogued_or_null_keyword() {
        assertThat(ActDictionary.lookup("Suspensión de pagos")).isEmpty();
        assertThat(ActDictionary.lookup(null)).isEmpty();
    }
}
