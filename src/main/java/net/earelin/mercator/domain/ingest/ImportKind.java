package net.earelin.mercator.domain.ingest;

/** The granularity of a historical-import request: a single publication day or a calendar month. */
public enum ImportKind {
    BY_DATE,
    BY_MONTH
}
