class RunnerFailure(Exception):
    def __init__(self, code: str, message: str, status_code: int):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


class BackendUnavailable(RunnerFailure):
    def __init__(self):
        super().__init__("BACKEND_UNAVAILABLE", "Execution backend is unavailable", 503)


class ExecutionTimedOut(RunnerFailure):
    def __init__(self):
        super().__init__("EXECUTION_TIMEOUT", "Python execution exceeded its time limit", 504)


class ExecutionFailed(RunnerFailure):
    def __init__(self):
        super().__init__("EXECUTION_FAILED", "Python execution failed or returned invalid output", 422)
