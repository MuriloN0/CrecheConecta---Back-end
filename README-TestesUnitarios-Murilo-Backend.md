## 1. Identificação
- **Aluno(s):** Murilo Novaes de Oliveira e RGM: 11231100551
- **Projeto (PFC):** CrecheConecta — Back-end
- **Branch:** feat/testes-automatizados

## 2. Resumo da entrega
Implementação de testes unitários do login em duas etapas, cobrindo validação de credenciais, confirmação do código enviado por e-mail e proteção criptográfica do código.
Foram executados 29 testes em três classes, com caminhos felizes, violações, casos-limite, testes parametrizados e isolamento das dependências.
Esta entrega contempla somente testes unitários, conforme a orientação publicada para a primeira etapa da atividade.

## 3. Cenários de testes unitários implementados
| # | Classe testada | Método / regra | Cenário | Tipo | Arquivo de teste | Método de teste |
|---|----------------|----------------|---------|------|------------------|-----------------|
| 1 | AuthService | iniciarLogin() | Credenciais corretas criam ação e enviam código | Feliz | AuthServiceTest.java | deveCriarAcaoEEnviarCodigoQuandoCredenciaisCorretas |
| 2 | AuthService | iniciarLogin() | Senha incorreta gera exceção sem enviar código | Violação | AuthServiceTest.java | deveLancarExcecaoENaoEnviarCodigoQuandoSenhaIncorreta |
| 3 | AuthService | iniciarLogin() | Quarta falha não bloqueia a conta | Limite | AuthServiceTest.java | deveBloquearContaSomenteAoAtingirQuintaFalha — parâmetro 4 |
| 4 | AuthService | iniciarLogin() | Quinta falha bloqueia a conta por 15 minutos | Limite | AuthServiceTest.java | deveBloquearContaSomenteAoAtingirQuintaFalha — parâmetro 5 |
| 5 | AuthService | iniciarLogin() | Usuário inexistente não recebe código | Violação | AuthServiceTest.java | deveRejeitarUsuarioInexistenteSemEnviarCodigo |
| 6 | AuthService | iniciarLogin() | Conta inativa é rejeitada sem conferir a senha | Violação | AuthServiceTest.java | deveRejeitarContaInativaSemConferirSenha |
| 7 | AuthService | iniciarLogin() | Conta bloqueada é rejeitada sem conferir a senha | Violação | AuthServiceTest.java | deveRejeitarContaBloqueadaSemConferirSenha |
| 8 | AuthService | iniciarLogin() | Bloqueio expirado permite login e limpa contadores | Limite | AuthServiceTest.java | devePermitirLoginQuandoBloqueioJaExpirou |
| 9 | AuthService | iniciarLogin() | Reenvio antes de 60 segundos é rejeitado | Violação | AuthServiceTest.java | deveImpedirReenvioAntesDeSessentaSegundos |
| 10 | AuthService | iniciarLogin() | Senha correta com 72 bytes é aceita | Limite | AuthServiceTest.java | deveRespeitarLimiteDeSetentaEDoisBytesDaSenha — parâmetro 72 |
| 11 | AuthService | iniciarLogin() | Senha com 73 bytes é rejeitada | Limite | AuthServiceTest.java | deveRespeitarLimiteDeSetentaEDoisBytesDaSenha — parâmetro 73 |
| 12 | AuthService | iniciarLogin() | Senha multibyte com 74 bytes é rejeitada | Limite | AuthServiceTest.java | deveRejeitarSenhaMultibyteQueUltrapassaLimiteUtf8 |
| 13 | ConfirmacaoLoginService | confirmar() | Código correto cria sessão e consome a ação | Feliz | ConfirmacaoLoginServiceTest.java | deveCriarSessaoEConsumirAcaoQuandoCodigoCorreto |
| 14 | ConfirmacaoLoginService | confirmar() | Código incorreto incrementa tentativas | Violação | ConfirmacaoLoginServiceTest.java | deveIncrementarTentativasQuandoCodigoIncorreto |
| 15 | ConfirmacaoLoginService | confirmar() | Quinta tentativa incorreta invalida a ação | Limite | ConfirmacaoLoginServiceTest.java | deveInvalidarAcaoNaQuintaTentativaIncorreta |
| 16 | ConfirmacaoLoginService | confirmar() | Código correto é aceito após quatro erros | Limite | ConfirmacaoLoginServiceTest.java | devePermitirCodigoCorretoAposQuatroTentativasIncorretas |
| 17 | ConfirmacaoLoginService | confirmar() | Ação expirada é rejeitada | Violação | ConfirmacaoLoginServiceTest.java | deveRejeitarAcaoExpiradaSemConferirCodigo |
| 18 | ConfirmacaoLoginService | confirmar() | Ação consumida não pode ser reutilizada | Violação | ConfirmacaoLoginServiceTest.java | deveRejeitarAcaoJaConsumidaSemCriarOutraSessao |
| 19 | ConfirmacaoLoginService | confirmar() | Dez erros recentes invalidam a ação | Limite | ConfirmacaoLoginServiceTest.java | deveInvalidarAcaoQuandoExistemDezErrosRecentes |
| 20 | SegredoVerificacaoService | proteger()/conferir() | Código correto é aceito no mesmo contexto | Feliz | SegredoVerificacaoServiceTest.java | deveAceitarCodigoProtegidoComMesmoUsuarioEAcao |
| 21 | SegredoVerificacaoService | conferir() | Código protegido para outro usuário é rejeitado | Violação | SegredoVerificacaoServiceTest.java | deveRejeitarCodigoProtegidoParaOutroUsuario |
| 22 | SegredoVerificacaoService | conferir() | Código protegido para outra ação é rejeitado | Violação | SegredoVerificacaoServiceTest.java | deveRejeitarCodigoProtegidoParaOutraAcao |
| 23 | SegredoVerificacaoService | conferir() | Código nulo é rejeitado | Limite | SegredoVerificacaoServiceTest.java | deveRejeitarCodigoNuloVazioOuIncorreto — parâmetro null |
| 24 | SegredoVerificacaoService | conferir() | Código vazio é rejeitado | Limite | SegredoVerificacaoServiceTest.java | deveRejeitarCodigoNuloVazioOuIncorreto — parâmetro vazio |
| 25 | SegredoVerificacaoService | conferir() | Código incorreto é rejeitado | Violação | SegredoVerificacaoServiceTest.java | deveRejeitarCodigoNuloVazioOuIncorreto — parâmetro 999999 |
| 26 | SegredoVerificacaoService | conferir() | Hash armazenado nulo é rejeitado | Limite | SegredoVerificacaoServiceTest.java | deveRejeitarConferenciaQuandoHashArmazenadoForNulo |
| 27 | SegredoVerificacaoService | Construtor | Chave com exatamente 32 bytes é aceita | Limite | SegredoVerificacaoServiceTest.java | deveAceitarChaveComExatamenteTrintaEDoisBytes |
| 28 | SegredoVerificacaoService | Construtor | Chave com 31 bytes gera exceção | Violação | SegredoVerificacaoServiceTest.java | deveRejeitarChaveComTrintaEUmBytes |
| 29 | SegredoVerificacaoService | Construtor | Base64 inválido gera exceção | Violação | SegredoVerificacaoServiceTest.java | deveRejeitarChaveQueNaoSejaBase64Valido |

Tipo: Feliz | Violação | Limite
**Total de cenários unitários:** 29, incluindo as variantes parametrizadas.

## 4. Cenários de testes de integração implementados
| # | Camadas envolvidas | Cenário | Arquivo de teste | Método de teste | Recurso usado |
|---|--------------------|---------|------------------|-----------------|---------------|
| Não se aplica | Não se aplica | Integração fora do escopo desta primeira entrega | Não se aplica | Não se aplica | Não se aplica |

**Total de cenários de integração:** 0 nesta contribuição.

## 5. Arquivos de teste criados ou alterados
| Arquivo (caminho completo) | Criado / Alterado | Qtd. de testes |
|----------------------------|-------------------|----------------|
| src/test/java/com/crecheconecta/service/AuthServiceTest.java | Criado | 12 |
| src/test/java/com/crecheconecta/service/ConfirmacaoLoginServiceTest.java | Criado | 7 |
| src/test/java/com/crecheconecta/security/SegredoVerificacaoServiceTest.java | Criado | 10 |

**Total de arquivos de teste:** 3  |  **Total de testes:** 29

## 6. Como executar os testes
```powershell
.\mvnw.cmd "-Dtest=AuthServiceTest,ConfirmacaoLoginServiceTest,SegredoVerificacaoServiceTest" test
```

Para executar a suíte completa do repositório:

```powershell
.\mvnw.cmd clean test
```

## 7. Evidências
- **Resultado da execução:** Os três arquivos foram executados juntos: `Tests run: 29, Failures: 0, Errors: 0, Skipped: 0`, com `BUILD SUCCESS`. Esse resultado corresponde à execução seletiva da contribuição; a execução completa do repositório não está documentada aqui.
- **Link do CI (se houver):** Não informado nesta documentação; evidência disponível da execução local.

## 8. Decisões e dificuldades
- **O que foi mockado e por quê:** Nos serviços de login, foram mockados repositórios, PasswordEncoder, envio de e-mail, proteção de segredos, geração de tokens e PlatformTransactionManager, conforme as dependências de cada classe. Isso permite executar as regras em memória, sem banco, rede ou contexto Spring. SegredoVerificacaoService foi testado diretamente com chave fictícia exclusiva dos testes.
- **Bugs encontrados pelos testes (se houver):** Nenhum bug de produção identificado nas execuções apresentadas.
- **Dificuldades:** Os serviços utilizam Instant.now(); as durações foram verificadas por intervalos entre antes e depois da chamada, sem Thread.sleep. O gerenciador de transações foi mockado, mas o TransactionTemplate executa os callbacks reais. Os testes não verificam persistência, locks ou commit/rollback reais. A entrega é restrita aos unitários. A organização do PR seguirá o Gitflow do projeto; qualquer diferença em relação à branch de origem e destino exigida pelo enunciado deve ser alinhada com o professor.

## 9. Checklist de entrega
- [ ] Todos os testes passam localmente com o comando da seção 6
- [ x ] Cada cenário listado nas seções 3 e 4 existe no código
- [ x ] Cada arquivo de teste alterado ou criado está listado na seção 5
- [ ] Mínimos do exercício atendidos (10 unitários em 3 classes; 4 de integração)
- [ x ] Nenhum teste com @Disabled, sem asserção ou com Thread.sleep
- [ x ] Professor adicionado como reviewer