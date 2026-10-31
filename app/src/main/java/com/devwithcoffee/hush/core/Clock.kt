package com.devwithcoffee.hush.core

/** Wall clock, swappable so UI recordings and tests can run on virtual time. */
object Clock {
    @Volatile var now: () -> Long = System::currentTimeMillis
}
