package net.earelin.mercator.domain.parse;

/**
 * The catalogue of Sección A act types Mercator recognises. {@link #name()} is the value stored in
 * {@code borme_act.act_type}; the type is code-owned (there is no {@code act_type} reference table),
 * so a new act type is a new enum value plus a parser branch.
 *
 * <p>Vocabulary ported from bormeparser (GPLv3, &copy; Pablo Castellano) &mdash; {@code acto.py};
 * see ADR-0012 and the catalogue in docs/specs/03-extraction.md.
 */
public enum ActType {

    CONSTITUCION,
    CAMBIO_DOMICILIO,
    NOMBRAMIENTO,
    CESE,
    REELECCION,
    REVOCACION,
    CAMBIO_OBJETO,
    CAMBIO_DENOMINACION,
    UNIPERSONALIDAD,
    DISOLUCION,
    EXTINCION,
    REAPERTURA,
    AMPLIACION_CAPITAL,
    REDUCCION_CAPITAL,
    FUSION,
    MODIF_ESTATUTOS,
    OTROS,
    FE_ERRATAS
}
