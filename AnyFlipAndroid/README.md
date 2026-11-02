# AnyFlip PDF - Android

Semplice applicazione Android in Java (Android 8.0+, minSdk 26), compilabile con GitHub Actions senza installare Android Studio.

## Compilazione online

1. Crea un repository GitHub **privato** vuoto (senza file README aggiuntivi).
2. Carica **tutti i file e le cartelle contenuti in questo progetto**, inclusa `.github/workflows/build-apk.yml`, nella cartella principale del repository. Per mantenere `.github` conviene usare Git sul computer, oppure l'interfaccia GitHub con upload file che supporti i percorsi delle sottocartelle.
3. Apri **Actions** -> **Build Android APK** -> **Run workflow** (o attendi la build attivata dal primo push).
4. Quando la build termina con successo, apri l'esecuzione e scarica l'artifact `AnyFlipAndroid-debug-apk`.
5. Estrai `app-debug.apk` dal file ZIP e installalo sul telefono dopo averlo verificato.

L'APK debug è firmato automaticamente con una chiave di debug generata dal runner. Non è una release firmata per la distribuzione pubblica; non preserva necessariamente la possibilità di aggiornamento tra build diverse.

## Sicurezza e limiti

- Nessuna pubblicità o tracking nel codice fornito.
- Autorizzazione richiesta: INTERNET. Non richiede accesso generale ai file: usa il selettore Android per salvare il PDF.
- Accetta link HTTPS per `anyflip.com`, `www.anyflip.com`, `online.anyflip.com` e legge i file solo da `online.anyflip.com`, senza seguire redirect.
- Le immagini vengono lette in memoria, quindi libri molto grandi potrebbero causare problemi di memoria; limite massimo 1000 pagine.
- Questo codice è un adattamento indipendente, non è l'eseguibile Go ufficiale di Lofter1. Non è stato testato su un dispositivo reale e potrebbe richiedere modifiche per particolari formati di flipbook.
- Scarica solo libri per cui l'editore consente il download in PDF.
