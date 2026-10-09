/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be approved by the European
 * Commission - subsequent versions of the EUPL (the "Licence"); You may not use this work
 * except in compliance with the Licence.
 *
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the Licence is distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the Licence for the specific language
 * governing permissions and limitations under the Licence.
 */

package eu.europa.ec.uilogic.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import eu.europa.ec.businesslogic.util.formatInstant
import eu.europa.ec.corelogic.extension.getExpiryDate
import eu.europa.ec.eudi.wallet.card.CardArtStore
import eu.europa.ec.eudi.wallet.card.CardDisplay
import eu.europa.ec.eudi.wallet.document.Document
import eu.europa.ec.eudi.wallet.document.IssuedDocument
import eu.europa.ec.resourceslogic.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

/**
 * GRNET fork: a payment card, a WE BUILD SCA-Card (DPC), as its issuer's display meta-data
 * describes it (rb-sca-card-dpc §2.9). See [PaymentCardFace].
 *
 * @property name the card's name, e.g. "Gold Mastercard"
 * @property lastFour the last four digits of the card number, if known
 * @property expiry the expiry date as on a card, e.g. "10/28": the credential's validity, as the
 * issuer shows it when the card is chosen; the display meta-data has no expiry of its own
 * @property networkName the payment network, e.g. "Mastercard", if known
 * @property typeLabel the product type, e.g. "Credit card", if known
 * @property cardArt the card art
 * @property issuerName the card issuer, e.g. "Partner Bank", if known: the bank whose card it is,
 * from the display meta-data's `issuer.branding`, not the issuing service that signed it
 * @property issuerLogo the card issuer's logo, if known
 */
data class PaymentCardUi(
    val name: String,
    val lastFour: String?,
    val expiry: String?,
    val networkName: String?,
    val typeLabel: String?,
    val cardArt: CardDisplay.Images,
    val issuerName: String? = null,
    val issuerLogo: URI? = null,
) {
    /** The card number as the wallet's lists show it, e.g. "Mastercard •••• 1234". */
    val summary: String?
        get() = listOfNotNull(networkName, lastFour?.let { "•••• $it" })
            .joinToString(" ")
            .ifBlank { null }

    companion object {
        /** The card of [document], or `null` if it is not a payment card with display meta-data. */
        suspend fun from(document: Document): PaymentCardUi? {
            val card = CardDisplay.of(document) ?: return null
            return PaymentCardUi(
                name = card.title,
                lastFour = card.lastFour,
                expiry = (document as? IssuedDocument)?.getExpiryDate()?.formatInstant(EXPIRY_PATTERN),
                networkName = card.networkName,
                typeLabel = card.type?.label,
                cardArt = card.cardArt,
                issuerName = card.issuerName,
                issuerLogo = card.issuerLogo.anyTheme?.let { runCatching { URI(it) }.getOrNull() },
            )
        }

        private const val EXPIRY_PATTERN = "MM/yy"
    }
}

/** The ISO/IEC 7810 ID-1 card's width to height, 85.60 by 53.98 mm. */
private const val CARD_ASPECT_RATIO = 85.60f / 53.98f

/** The width of a card shown in a list row instead of an icon. */
const val PAYMENT_CARD_THUMBNAIL_WIDTH = 56

private sealed interface CardArt {
    data object Loading : CardArt
    data object Missing : CardArt
    data class Loaded(val bitmap: ImageBitmap) : CardArt
}

/**
 * GRNET fork: a payment card drawn as its issuer draws it on the card choice page: the card art,
 * with the card number's last four digits and the expiry date over it when [showDetails] is set.
 * The art is the copy wallet-core keeps on the device, for the active theme. A card without art
 * is drawn plain, with its name.
 *
 * The positions and sizes of the text are fractions of the card's width and height, the issuer's
 * own, so that the card looks the same at every size. In a list row, where the text would be too
 * small to read, leave [showDetails] off and show the number beside the card.
 */
@Composable
fun PaymentCardFace(
    card: PaymentCardUi,
    modifier: Modifier = Modifier,
    showDetails: Boolean = true,
) {
    val context = LocalContext.current.applicationContext
    val url = card.cardArt.forTheme(darkTheme = isSystemInDarkTheme())
    val cardArt by produceState<CardArt>(initialValue = CardArt.Loading, url) {
        value = withContext(Dispatchers.IO) {
            CardArtStore(context).get(url)
                ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                ?.let { CardArt.Loaded(it.asImageBitmap()) }
                ?: CardArt.Missing
        }
    }
    val description = card.lastFour
        ?.let { stringResource(R.string.payment_card_content_description, card.name, it) }
        ?: card.name

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(CARD_ASPECT_RATIO)
            .semantics { contentDescription = description },
    ) {
        val width = maxWidth
        val height = maxHeight
        // The card art has rounded corners of its own, about 2.4% of its width.
        val shape = RoundedCornerShape(width * 0.024f)
        when (val art = cardArt) {
            is CardArt.Loaded -> Image(
                bitmap = art.bitmap,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape),
            )

            CardArt.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )

            CardArt.Missing -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primary),
            ) {
                if (showDetails) {
                    CardText(
                        text = card.name,
                        size = width * 0.05f,
                        modifier = Modifier.offset(x = width * 0.08f, y = height * 0.12f),
                    )
                }
            }
        }

        if (showDetails && cardArt != CardArt.Loading) {
            card.lastFour?.let { lastFour ->
                CardText(
                    text = "•••• •••• •••• $lastFour",
                    size = width * 0.062f,
                    monospace = true,
                    letterSpacing = 0.04f,
                    modifier = Modifier.offset(x = width * 0.08f, y = height * 0.51f),
                )
            }
            card.expiry?.let { expiry ->
                Column(modifier = Modifier.offset(x = width * 0.08f, y = height * 0.72f)) {
                    CardText(
                        text = stringResource(R.string.payment_card_expiry_date).uppercase(),
                        size = width * 0.026f,
                        letterSpacing = 0.08f,
                        modifier = Modifier.alpha(0.8f),
                    )
                    CardText(text = expiry, size = width * 0.046f, monospace = true)
                }
            }
        }
    }
}

/** White text with a soft shadow, legible on light and dark card art alike. */
@Composable
private fun CardText(
    text: String,
    size: Dp,
    modifier: Modifier = Modifier,
    monospace: Boolean = false,
    letterSpacing: Float = 0f,
) {
    val density = LocalDensity.current
    val fontSize = with(density) { size.toSp() }
    Text(
        text = text,
        modifier = modifier,
        maxLines = 1,
        overflow = TextOverflow.Clip,
        style = TextStyle(
            color = Color.White,
            fontSize = fontSize,
            lineHeight = fontSize * 1.25f,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            letterSpacing = letterSpacing.em,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.5f),
                offset = with(density) { Offset(0f, 1.dp.toPx()) },
                blurRadius = with(density) { 2.dp.toPx() },
            ),
        ),
    )
}
