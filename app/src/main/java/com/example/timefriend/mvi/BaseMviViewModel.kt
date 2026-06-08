package com.example.timefriend.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseMviViewModel<State, Intent, Effect> : ViewModel() {

    private val initialState: State by lazy { createInitialState() }

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _effect = Channel<Effect>(Channel.BUFFERED)
    val effect = _effect.receiveAsFlow()

    val currentState: State get() = _state.value

    abstract fun createInitialState(): State

    abstract fun handleIntent(intent: Intent)

    protected fun setState(reducer: State.() -> State) {
        _state.value = currentState.reducer()
    }

    protected fun sendEffect(builder: () -> Effect) {
        viewModelScope.launch {
            _effect.send(builder())
        }
    }
}
