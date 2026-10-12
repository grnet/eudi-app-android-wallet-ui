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

package eu.europa.ec.dashboardfeature.ui.documents.group

import androidx.lifecycle.viewModelScope
import eu.europa.ec.dashboardfeature.interactor.DocumentInteractorGetDocumentsPartialState
import eu.europa.ec.dashboardfeature.interactor.DocumentsInteractor
import eu.europa.ec.dashboardfeature.ui.documents.list.model.DocumentUi
import eu.europa.ec.eudi.wallet.document.DocumentId
import eu.europa.ec.uilogic.component.ListItemMainContentDataUi
import eu.europa.ec.uilogic.component.content.ContentErrorConfig
import eu.europa.ec.uilogic.mvi.MviViewModel
import eu.europa.ec.uilogic.mvi.ViewEvent
import eu.europa.ec.uilogic.mvi.ViewSideEffect
import eu.europa.ec.uilogic.mvi.ViewState
import eu.europa.ec.uilogic.navigation.DashboardScreens
import eu.europa.ec.uilogic.navigation.helper.generateComposableArguments
import eu.europa.ec.uilogic.navigation.helper.generateComposableNavigationLink
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.core.annotation.InjectedParam
import org.koin.core.annotation.KoinViewModel

// GRNET fork: a person's PIDs, grouped as one row in Documents (see PidGroups.kt), listed as the
// Documents list shows each of them; each opens its details.

data class State(
    val isLoading: Boolean = true,
    val error: ContentErrorConfig? = null,
    val documents: List<DocumentUi> = emptyList(),
) : ViewState

sealed class Event : ViewEvent {
    data object GetDocuments : Event()
    data object Pop : Event()
    data class GoToDocumentDetails(val docId: DocumentId) : Event()
}

sealed class Effect : ViewSideEffect {
    sealed class Navigation : Effect() {
        data object Pop : Navigation()
        data class SwitchScreen(val screenRoute: String) : Navigation()
    }
}

@KoinViewModel
class DocumentGroupViewModel(
    private val interactor: DocumentsInteractor,
    @InjectedParam private val documentIds: String,
) : MviViewModel<Event, State, Effect>() {

    private val ids: Set<DocumentId>
        get() = documentIds.split(",").filter { it.isNotBlank() }.toSet()

    private var job: Job? = null

    override fun setInitialState(): State = State()

    override fun handleEvents(event: Event) {
        when (event) {
            is Event.GetDocuments -> getDocuments()
            is Event.Pop -> setEffect { Effect.Navigation.Pop }
            is Event.GoToDocumentDetails -> setEffect {
                Effect.Navigation.SwitchScreen(
                    screenRoute = generateComposableNavigationLink(
                        screen = DashboardScreens.DocumentDetails,
                        arguments = generateComposableArguments(mapOf("documentId" to event.docId))
                    )
                )
            }
        }
    }

    private fun getDocuments() {
        job?.cancel()
        job = viewModelScope.launch {
            interactor.getDocuments().collect { response ->
                when (response) {
                    is DocumentInteractorGetDocumentsPartialState.Failure -> setState {
                        copy(
                            isLoading = false,
                            error = ContentErrorConfig(
                                onRetry = { setEvent(Event.GetDocuments) },
                                errorSubTitle = response.error,
                                onCancel = { setEvent(Event.Pop) },
                            ),
                        )
                    }

                    is DocumentInteractorGetDocumentsPartialState.Success -> {
                        val documents = response.allDocuments.items
                            .mapNotNull { it.payload as? DocumentUi }
                            .filter { it.uiData.itemId in ids }
                            .sortedBy {
                                (it.uiData.mainContentData as? ListItemMainContentDataUi.Text)
                                    ?.text?.lowercase()
                            }
                        // All of them deleted from their details: nothing left to show.
                        if (documents.isEmpty()) {
                            setEffect { Effect.Navigation.Pop }
                        } else {
                            setState { copy(isLoading = false, error = null, documents = documents) }
                        }
                    }
                }
            }
        }
    }
}
