#!/bin/bash

echo "=== Aggiornamento completo dei package e import di ConnectionManager ==="

# 1. Aggiorna i package declaration nei file di connection
echo "1. Aggiornando i package declarations..."

sed -i 's/package com\.geosit\.gnss\.data\.connection/package com.geosit.gnss.ui.screens.connection/' \
    app/src/main/java/com/geosit/gnss/ui/screens/connection/ConnectionManager.kt

sed -i 's/package com\.geosit\.gnss\.data\.connection/package com.geosit.gnss.ui.screens.connection/' \
    app/src/main/java/com/geosit/gnss/ui/screens/connection/ConnectionService.kt

# Aggiorna anche gli altri service se necessario
for file in BluetoothConnectionService.kt TcpConnectionService.kt UsbConnectionService.kt; do
    if [ -f "app/src/main/java/com/geosit/gnss/ui/screens/connection/$file" ]; then
        sed -i 's/package com\.geosit\.gnss\.data\.connection/package com.geosit.gnss.ui.screens.connection/' \
            "app/src/main/java/com/geosit/gnss/ui/screens/connection/$file"
    fi
done

# 2. Aggiorna tutti gli import statements
echo "2. Aggiornando tutti gli import statements..."

# Lista di tutti i file che potrebbero avere l'import sbagliato
files=(
    "app/src/main/java/com/geosit/gnss/di/AppModule.kt"
    "app/src/main/java/com/geosit/gnss/ui/viewmodel/DashboardViewModel.kt"
    "app/src/main/java/com/geosit/gnss/ui/viewmodel/ConnectionViewModel.kt"
    "app/src/main/java/com/geosit/gnss/ui/viewmodel/RecordingViewModel.kt"
    "app/src/main/java/com/geosit/gnss/data/recording/RecordingRepository.kt"
    "app/src/main/java/com/geosit/gnss/data/gnss/UbxMessageEnabler.kt"
)

# Aggiorna gli import di ConnectionManager
for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "  Aggiornando: $file"
        sed -i 's/import com\.geosit\.gnss\.data\.connection\.ConnectionManager/import com.geosit.gnss.ui.screens.connection.ConnectionManager/' "$file"
        sed -i 's/import com\.geosit\.gnss\.data\.connection\.ConnectionService/import com.geosit.gnss.ui.screens.connection.ConnectionService/' "$file"
    fi
done

# 3. Aggiorna anche gli import negli altri file di connection se ci sono cross-references
echo "3. Aggiornando cross-references nei file di connection..."

connection_files=(
    "app/src/main/java/com/geosit/gnss/ui/screens/connection/BluetoothConnectionService.kt"
    "app/src/main/java/com/geosit/gnss/ui/screens/connection/TcpConnectionService.kt"
    "app/src/main/java/com/geosit/gnss/ui/screens/connection/UsbConnectionService.kt"
)

for file in "${connection_files[@]}"; do
    if [ -f "$file" ]; then
        # Aggiorna import di ConnectionService se presente
        sed -i 's/import com\.geosit\.gnss\.data\.connection\.ConnectionService/import com.geosit.gnss.ui.screens.connection.ConnectionService/' "$file"
    fi
done

echo ""
echo "=== Aggiornamenti completati! ==="
echo ""
echo "Prossimi passi:"
echo "1. Build -> Clean Project"
echo "2. Build -> Rebuild Project"
echo "3. Se ci sono ancora errori, eseguire: File -> Invalidate Caches and Restart"
