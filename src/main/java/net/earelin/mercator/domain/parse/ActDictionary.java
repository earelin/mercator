package net.earelin.mercator.domain.parse;

import static java.util.Map.entry;

import io.micronaut.core.annotation.Nullable;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a BORME act keyword to its canonical {@link ActType}. A keyword not catalogued here resolves
 * to empty so the act splitter can bucket it as {@link ActType#OTROS} without losing the raw text.
 *
 * <p>Vocabulary ported from bormeparser (GPLv3, &copy; Pablo Castellano) &mdash; {@code acto.py};
 * see ADR-0012 and docs/specs/03-extraction.md.
 */
public final class ActDictionary {

    private static final Map<String, ActType> BY_KEYWORD = Map.ofEntries(
        entry("Constitución", ActType.CONSTITUCION),
        entry("Cambio de domicilio social", ActType.CAMBIO_DOMICILIO),
        entry("Nombramientos", ActType.NOMBRAMIENTO),
        entry("Ceses/Dimisiones", ActType.CESE),
        entry("Reelecciones", ActType.REELECCION),
        entry("Revocaciones", ActType.REVOCACION),
        entry("Cambio de objeto social", ActType.CAMBIO_OBJETO),
        entry("Cambio de denominación social", ActType.CAMBIO_DENOMINACION),
        entry("Declaración de unipersonalidad", ActType.UNIPERSONALIDAD),
        entry("Sociedad unipersonal", ActType.UNIPERSONALIDAD),
        entry("Disolución", ActType.DISOLUCION),
        entry("Extinción", ActType.EXTINCION),
        entry("Reapertura hoja registral", ActType.REAPERTURA),
        entry("Ampliación de capital", ActType.AMPLIACION_CAPITAL),
        entry("Reducción de capital", ActType.REDUCCION_CAPITAL),
        entry("Fusión por absorción", ActType.FUSION),
        entry("Modificaciones estatutarias", ActType.MODIF_ESTATUTOS),
        entry("Otros conceptos", ActType.OTROS),
        entry("Fe de erratas", ActType.FE_ERRATAS));

    private ActDictionary() {
    }

    /** The canonical act type for {@code keyword}, or empty if it is not catalogued. */
    public static Optional<ActType> lookup(@Nullable String keyword) {
        if (keyword == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(BY_KEYWORD.get(keyword.strip()));
    }
}
