#!/bin/bash
# Konfiguracja automatycznego wznawiania sesji
# Uruchom raz: ./setup_auto_resume.sh

set -e

SCRIPT_DIR="$HOME/Documents/VBT"
AUTO_RESUME_SCRIPT="$SCRIPT_DIR/auto_resume_session.sh"
SYNC_SCRIPT="$SCRIPT_DIR/sync_to_server.sh"

echo "=== Konfiguracja automatycznego wznawiania sesji Claude ==="
echo ""

# Nadaj uprawnienia wykonywania
echo "1. Nadaję uprawnienia wykonywania skryptom..."
chmod +x "$AUTO_RESUME_SCRIPT"
chmod +x "$SYNC_SCRIPT"
echo "✓ Uprawnienia ustawione"
echo ""

# Test połączenia
echo "2. Testuję połączenie z serwerem..."
SERVER="ubuntu@130.61.232.212"
SSH_KEY="$HOME/ssh-key-2025-12-19.key"
if ssh -i "$SSH_KEY" -o ConnectTimeout=5 "$SERVER" "echo 'OK'" > /dev/null 2>&1; then
    echo "✓ Połączenie SSH działa"
else
    echo "✗ BŁĄD: Nie można połączyć się z serwerem"
    exit 1
fi
echo ""

echo "3. Wykonuję pierwszą synchronizację..."
if bash "$SYNC_SCRIPT"; then
    echo "✓ Synchronizacja zakończona"
else
    echo "⚠ Synchronizacja zakończona z ostrzeżeniami (może to być normalne przy pierwszym uruchomieniu)"
fi
echo ""

# Utworzenie usługi systemd (opcjonalne)
echo "4. Czy chcesz utworzyć usługę systemd do automatycznego uruchamiania? (t/n)"
read -r answer

if [ "$answer" = "t" ] || [ "$answer" = "T" ]; then
    SERVICE_FILE="$HOME/.config/systemd/user/claude-auto-resume.service"
    mkdir -p "$HOME/.config/systemd/user"

    cat > "$SERVICE_FILE" << EOF
[Unit]
Description=Claude Code Auto Resume Service
After=network.target

[Service]
Type=simple
ExecStart=$AUTO_RESUME_SCRIPT
Restart=always
RestartSec=10
StandardOutput=append:$SCRIPT_DIR/auto_resume.log
StandardError=append:$SCRIPT_DIR/auto_resume_error.log

[Install]
WantedBy=default.target
EOF

    echo "✓ Utworzono plik usługi: $SERVICE_FILE"
    echo ""
    echo "Aby uruchomić usługę:"
    echo "  systemctl --user daemon-reload"
    echo "  systemctl --user enable claude-auto-resume.service"
    echo "  systemctl --user start claude-auto-resume.service"
    echo ""
    echo "Aby sprawdzić status:"
    echo "  systemctl --user status claude-auto-resume.service"
else
    echo ""
    echo "Aby uruchomić skrypt ręcznie w tle:"
    echo "  nohup $AUTO_RESUME_SCRIPT > $SCRIPT_DIR/auto_resume.log 2>&1 &"
    echo ""
    echo "Aby sprawdzić logi:"
    echo "  tail -f $SCRIPT_DIR/auto_resume.log"
fi

echo ""
echo "=== Konfiguracja zakończona ==="
echo ""
echo "Dostępne komendy:"
echo "  ./sync_to_server.sh              - Synchronizacja projektu na serwer"
echo "  ./auto_resume_session.sh         - Uruchom automatyczne wznawianie (blokujące)"
echo "  nohup ./auto_resume_session.sh & - Uruchom w tle"
echo "  tail -f auto_resume.log          - Podgląd logów"
