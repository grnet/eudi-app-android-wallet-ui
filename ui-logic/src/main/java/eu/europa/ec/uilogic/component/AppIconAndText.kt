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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.preview.PreviewTheme
import eu.europa.ec.uilogic.component.preview.ThemeModePreviews
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL
import eu.europa.ec.uilogic.component.wrap.WrapImage
import kotlinx.serialization.Serializable

@Serializable
data class AppIconAndTextDataUi(
    val appIcon: IconDataUi = AppIcons.LogoIconAndText,
)

@Composable
fun AppIconAndText(
    modifier: Modifier = Modifier,
    appIconAndTextData: AppIconAndTextDataUi
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(
            space = SPACING_SMALL.dp,
            alignment = Alignment.CenterHorizontally
        ),
        verticalAlignment = Alignment.Top
    ) {
        // GRNET fork: the EUDI logo beside the gov.gr beta logo, gov.gr the larger
        // of the two. Each is given a height, and its width from its drawable's own
        // aspect ratio: ContentScale.Fit alone only ever scales down, so a logo
        // asked to be taller than its drawable would stay at the drawable's size.
        // 36 + 8 + 44dp tall makes a row about 266dp wide, which fits beside the
        // home screen's menu button on a 360dp phone.
        WrapImage(
            iconData = appIconAndTextData.appIcon,
            modifier = Modifier
                .height(36.dp)
                .aspectRatio(aspectRatioOf(appIconAndTextData.appIcon))
                .align(Alignment.CenterVertically),
            contentScale = ContentScale.Fit
        )
        WrapImage(
            iconData = AppIcons.GovGrBeta,
            modifier = Modifier
                .height(44.dp)
                .aspectRatio(aspectRatioOf(AppIcons.GovGrBeta))
                .align(Alignment.CenterVertically),
            contentScale = ContentScale.Fit
        )
    }
}

/** Width over height of the icon's drawable, as it declares itself; 1 if unknown. */
@Composable
private fun aspectRatioOf(iconData: IconDataUi): Float =
    iconData.resourceId
        ?.let { painterResource(it).intrinsicSize }
        ?.takeIf { it.isSpecified && it.height > 0f }
        ?.let { it.width / it.height }
        ?: 1f

@ThemeModePreviews
@Composable
private fun AppIconAndTextPreview() {
    PreviewTheme {
        AppIconAndText(
            appIconAndTextData = AppIconAndTextDataUi()
        )
    }
}