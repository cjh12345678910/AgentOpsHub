from typing import Dict

from models import AgentRunResult
from state_machine import StateMachine


class AgentRunner:
    def __init__(self) -> None:
        self._state_machine = StateMachine()

    def run(self, payload: Dict) -> AgentRunResult:
        return self._state_machine.run(payload)
