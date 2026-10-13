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

package eu.europa.ec.uilogic.component.wrap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.europa.ec.uilogic.component.utils.SPACING_SMALL

sealed interface StickyBottomType {
    data class OneButton(
        val config: ButtonConfig
    ) : StickyBottomType

    /**
     * @property primaryButtonFitsContent GRNET fork: half each, as by default, unless the primary
     * button's label needs more than half to fit on one line; then the primary button takes
     * exactly that width and the secondary the rest, so the secondary is never wider than half.
     * For a primary label that must not wrap, such as a payment's amount.
     */
    data class TwoButtons(
        val primaryButtonConfig: ButtonConfig,
        val secondaryButtonConfig: ButtonConfig,
        val secondaryButtonContent: @Composable RowScope.() -> Unit,
        val primaryButtonFitsContent: Boolean = false,
    ) : StickyBottomType

    data object Generic : StickyBottomType
}

data class StickyBottomConfig(
    val type: StickyBottomType,
    val showDivider: Boolean = true,
)

@Composable
fun WrapStickyBottomContent(
    modifier: Modifier = Modifier,
    stickyBottomConfig: StickyBottomConfig,
    content: @Composable () -> Unit,
) {
    when (val stickyBottomType = stickyBottomConfig.type) {
        is StickyBottomType.OneButton -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (stickyBottomConfig.showDivider) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                Row(
                    modifier = modifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    WrapButton(
                        modifier = Modifier.fillMaxWidth(),
                        buttonConfig = stickyBottomType.config,
                    ) {
                        content()
                    }
                }
            }
        }

        is StickyBottomType.TwoButtons -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (stickyBottomConfig.showDivider) {
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }

                BoxWithConstraints(modifier = modifier) {
                    val half = (maxWidth - SPACING_SMALL.dp) / 2
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(SPACING_SMALL.dp)
                    ) {
                        WrapButton(
                            modifier = Modifier.weight(1f),
                            buttonConfig = stickyBottomType.secondaryButtonConfig
                        ) {
                            stickyBottomType.secondaryButtonContent(this)
                        }
                        WrapButton(
                            // Measured before the weighted button: at least half, more only if
                            // its label needs it to fit on one line.
                            modifier = if (stickyBottomType.primaryButtonFitsContent) {
                                Modifier.widthIn(min = half)
                            } else {
                                Modifier.weight(1f)
                            },
                            buttonConfig = stickyBottomType.primaryButtonConfig
                        ) {
                            content()
                        }
                    }
                }
            }
        }

        is StickyBottomType.Generic -> {
            content()
        }
    }
}