# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchcorespi
    SOURCES
    $<TARGET_OBJECTS:searchcorespi_flush>
    $<TARGET_OBJECTS:searchcorespi_index>
    $<TARGET_OBJECTS:searchcorespi_plugin>
    INSTALL lib64
    DEPENDS
)
