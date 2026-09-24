# PiP nativo: overlay para a SystemUI IHU629G

## Introducao

Este kit ativa `com.android.systemui.pip.PipUI` na ROM Android 9/API 28
para Geely Ex2 firmware 1111.
Este kit nao adiciona suporte a apps que nao implementam PiP.

O PiP (Picture-in-Picture) da central já conseguia exibir vídeos em uma janela pequena, mas não respondia corretamente aos toques. A investigação identificou que a SystemUI continha as classes responsáveis pelo PiP, porém o componente com.android.systemui.pip.PipUI estava ausente da lista de inicialização. Sem ele, não era registrado o consumidor de entrada que permite movimentar a janela e acessar seus controles.

A solução utiliza um Runtime Resource Overlay (RRO): um pequeno APK que substitui um recurso de configuração da SystemUI sem modificar seu APK original. O overlay redefine o array config_systemUIServiceComponents, preservando os quatro componentes existentes e acrescentando PipUI. Como a ROM exige assinatura de plataforma para instalar overlays, utilizamos a chave compatível já disponível no projeto.

Após habilitar o overlay e reiniciar a SystemUI, o controlador de PiP foi carregado e passou a receber os eventos de toque. O arraste da janela e o retorno à tela cheia foram validados com o YouTube ReVanced. A correção atua no PiP nativo do sistema, podendo beneficiar outros aplicativos compatíveis, e pode ser revertida desativando o overlay.

## Instalacao

Copie esta pasta inteira para um computador Windows com PowerShell e ADB.
A central precisa estar conectada, com ADB autorizado e `adb shell id -u`
retornando `0`. 
Use o identificador exibido por `adb devices` em lugar do IP do exemplo.

```powershell
.\Manage-PipOverlay.ps1 -Serial 192.168.1.172:5555 -Action Check
.\Manage-PipOverlay.ps1 -Serial 192.168.1.172:5555 -Action Install
```

`Check` e somente leitura no dispositivo. `Install` instala o APK, habilita
o overlay para o usuario de sistema 0 e reinicia somente a SystemUI.
A interface da central desaparece brevemente. Execute com o veiculo parado.
O script prepara uma reversao em 90 segundos, cancelada apenas depois de
confirmar que PipUI carregou e que o PID da SystemUI permaneceu estavel
durante a verificacao curta. Isso nao substitui o teste visual.

O instalador recusa uma SystemUI com SHA-256 diferente da validada:
`0C9AB49B1CCF83133B3CBCAB1CC1D980E97C6472FE9D06F65E2BEF07D0737332`.
O mesmo modelo de central nao garante a mesma ROM. Em outra versao, examine
a lista original de componentes e preserve todos eles antes de adaptar o overlay.
Outros overlays personalizados tambem precisam ser considerados.

## Reversao

```powershell
.\Manage-PipOverlay.ps1 -Serial 192.168.1.172:5555 -Action Disable
```

Isso desativa o overlay e reinicia a SystemUI, sem excluir o APK.
Para remover o pacote, depois de desativar:

```powershell
adb -s 192.168.1.172:5555 uninstall com.geely.ex2.mica.pipoverlayprobe
```

## Persistencia e validacao

O APK fica em `/data/app` e o estado habilitado e salvo pelo Android em
`/data/system/overlays.xml`. Esse arquivo foi conferido com `isEnabled="true"`
na central original. Portanto, deve persistir ao boot sem script de inicializacao.
Um boot completo ainda nao foi testado. Reset de fabrica, desinstalacao,
desativacao e atualizacoes de firmware podem remover ou invalidar a correcao.

Na central original foram validados: instalacao, habilitacao, carga de PipUI,
registro do consumidor de entrada, arraste e retorno a tela cheia no ReVanced.
O usuario informou funcionamento geral normal. Outros apps e outras centrais
ainda nao foram testados. A sintaxe do script empacotado foi verificada
localmente, e sua acao `Check` passou na central original sem alterar seu estado.
As acoes `Install` e `Disable` do script nao foram executadas de ponta a ponta;
os passos de instalacao e ativacao equivalentes foram executados manualmente.

Depois de um boot feito pelo usuario, `-Action Check` mostra o estado habilitado.
Confirme tambem que um video em PiP continua aceitando toque e arraste.

## Conteudo e assinatura

- `pip-overlay-platform.apk`: APK exato usado na validacao.
- `source/`: manifesto e array de recursos utilizados para compilar o APK.
- `Manage-PipOverlay.ps1`: verificacao, instalacao e desativacao.
- `guard.sh` e `rollback.sh`: reversao local no dispositivo.

O APK nao tem codigo DEX nem servico proprio. Substitui um unico array de
recursos, preservando as quatro entradas originais e acrescentando PipUI.
O APK original da SystemUI e as particoes de sistema nao sao modificados.
O nome de pacote contem `probe` para manter a identidade do pacote validado.

Esta ROM exige assinatura de plataforma para instalar overlays. Foi usada a
chave ja existente no projeto, cujo certificado corresponde ao da central.
Nenhuma chave privada esta incluida neste kit. Uma central com certificado
diferente pode recusar a instalacao.

SHA-256 do APK distribuido:
`47F434857D7F2E175ECED4F591342667AA31B91A61636EEE0FFF19BAE68EF725`.

Referencia: https://source.android.com/docs/core/runtime/rros

## Isenção de responsabilidade

Esta solução é experimental, fornecida “no estado em que se encontra”, sem garantia de compatibilidade, estabilidade ou funcionamento em todas as centrais e versões de firmware. A validação realizada se limita ao equipamento e às condições descritas neste documento.
A instalação e o uso são de responsabilidade do usuário. Alterações na configuração da SystemUI podem provocar falhas na interface e exigir procedimentos de recuperação. Na medida permitida pela legislação aplicável, os autores e colaboradores não se responsabilizam por danos ou prejuízos decorrentes do uso desta solução. Essa declaração não afasta direitos ou responsabilidades que não possam ser excluídos por lei.
Execute a instalação e os testes com o veículo estacionado em local seguro. Não utilize vídeos ou controles que desviem a atenção durante a condução.