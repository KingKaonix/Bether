# Bether — Tests

Python unit and integration tests for the Linux client and shared models.

## Test Layout

```
tests/
├── conftest.py                    # pytest fixtures
├── linux_client/
│   ├── test_connection_manager.py # LinuxConnectionManager tests
│   └── test_socks5_client.py      # SOCKS5 client tests
├── shared/
│   ├── test_models.py             # Shared dataclass tests
│   └── test_utils.py              # Utility tests
└── integration/
    ├── test_carrier_bypass.py     # Carrier bypass config validation
    └── test_socks5_integration.py # End-to-end SOCKS5 integration
```

## Running Tests

From the repository root:

```bash
# Install dependencies
pip install pytest pyyaml

# Run all tests
python -m pytest tests/ -v

# Run specific directory
python -m pytest tests/linux_client/ -v
python -m pytest tests/shared/ -v
python -m pytest tests/integration/ -v

# Run a single test file
python -m pytest tests/shared/test_models.py -v
```

## CI

`.github/workflows/linux-build.yml` runs `python -m pytest tests/` on Python 3.10/3.11/3.12 with flake8 linting and black formatting checks.

`.github/workflows/integration-test.yml` runs integration tests nightly and on push/PR, validates the carrier bypass config defaults, and runs a bandit security scan.

## Writing Tests

- Use `pytest` style (plain `assert`, no unittest classes required)
- Name test files `test_*.py` and test functions `test_*`
- Place shared fixtures in `conftest.py`
- Keep tests deterministic; avoid real network calls in unit tests

## License

MIT