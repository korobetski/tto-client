package com.tripletriad.data

import org.jetbrains.compose.resources.ExperimentalResourceApi
import tripletriad.shared.generated.resources.Res

const val CARD_CATALOG_PATH: String = "files/cards.json"

const val NPC_CATALOG_PATH: String = "files/npcs.json"

const val CAMPAIGN_CATALOG_PATH: String = "files/campaigns.json"

const val STARTER_CATALOG_PATH: String = "files/starters.json"

const val FORMAT_CATALOG_PATH: String = "files/formats.json"

@OptIn(ExperimentalResourceApi::class)
suspend fun loadCardCatalog(): CardCatalog =
    CardCatalogParser.parse(Res.readBytes(CARD_CATALOG_PATH).decodeToString())

@OptIn(ExperimentalResourceApi::class)
suspend fun loadNpcCatalog(): NpcCatalog =
    NpcCatalogParser.parse(Res.readBytes(NPC_CATALOG_PATH).decodeToString())

@OptIn(ExperimentalResourceApi::class)
suspend fun loadCampaignCatalog(): CampaignCatalog =
    CampaignCatalogParser.parse(Res.readBytes(CAMPAIGN_CATALOG_PATH).decodeToString())

@OptIn(ExperimentalResourceApi::class)
suspend fun loadStarterCatalog(): StarterCatalog =
    StarterCatalogParser.parse(Res.readBytes(STARTER_CATALOG_PATH).decodeToString())

@OptIn(ExperimentalResourceApi::class)
suspend fun loadFormatCatalog(): FormatCatalog =
    FormatCatalogParser.parse(Res.readBytes(FORMAT_CATALOG_PATH).decodeToString())
