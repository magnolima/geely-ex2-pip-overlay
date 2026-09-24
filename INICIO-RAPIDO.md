# Habilitar PiP com um comando

Extraia a pasta inteira do kit. No terminal, dentro dessa pasta:

```bat
enable-pip.bat 192.168.1.172:5555
```

No PowerShell, use `./enable-pip.bat 192.168.1.172:5555`.
Substitua pelo IPv4 e porta ADB da central. O comando conecta nesse endereco,
confirma que ele esta disponivel e executa `Check` seguido de `Install`.
Nao e necessario consultar ou copiar outro ID: o proprio endereco e o serial
ADB selecionado explicitamente em todas as operacoes da central.
Se qualquer etapa falhar, o comando para e retorna um codigo de erro.

Requisitos:

- Central acessivel na rede, com ADB autorizado e shell root (`uid 0`).
- Mesma SystemUI validada pelo kit; uma diferente sera recusada pelo Check.
- Windows com PowerShell e Android SDK Platform-Tools. O kit nao inclui ADB:
  disponibilize `adb.exe` no PATH ou extraia a pasta completa `platform-tools`
  dentro da pasta do kit, mantendo tambem suas DLLs.

O comando nao habilita ADB/root na central, nao altera a rede e nao reinicia
o veiculo. A ativacao reinicia somente a SystemUI, interrompendo brevemente
a interface. Execute com o veiculo parado. Se o overlay ja estiver habilitado,
o instalador apenas informa esse estado sem reinstalar ou reiniciar.

Para reverter:

```bat
disable-pip.bat 192.168.1.172:5555
```

A reversao tambem conecta automaticamente. Desativa o overlay e reinicia
a SystemUI, mantendo o APK instalado. Nao exige passar no Check de instalacao,
para permitir reverter mesmo se a SystemUI tiver sido atualizada.

Os wrappers foram verificados localmente; nao foram usados para reinstalar
a correcao na central. Veja README.md para o diagnostico, assinatura,
persistencia esperada e limites da validacao realizada.
