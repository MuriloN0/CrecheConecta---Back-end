## 1. Identificação

- **Aluno(s):** Murilo Novaes de Oliveira - RGM: 11231100551
- **Projeto (PFC):** CrecheConecta — Back-end
- **Branch:** feature/testes-integracao

## 2. Resumo da entrega

Implementação de 14 testes de integração para AlunoController e AlunoResponsavelController, cobrindo os oito endpoints desses controllers.

Os testes exercitam chamadas HTTP reais, autenticação, autorização, validação de entrada, tratamento de erros, persistência e histórico dos vínculos entre alunos e responsáveis.

## 3. Configuração do ambiente de teste

- **Estratégia:** @SpringBootTest com RANDOM_PORT e chamadas HTTP reais utilizando RestClient.
- **Banco de teste:** H2 em memória, em modo de compatibilidade PostgreSQL, com ddl-auto=create-drop.
- **Arquivo de configuração:** src/test/resources/application-test.properties.
- **Isolamento de dados:** usuários com e-mails únicos gerados por UUID; cada teste cria seus próprios alunos e vínculos, sem depender de dados cadastrados manualmente ou da ordem de execução.
- **Componentes mockados e motivo:** somente EmailService, para capturar o código de autenticação e impedir o envio externo de e-mails pelo Resend.

A classe abstrata BaseControllerIntegrationTest centraliza a configuração, os helpers HTTP e a autenticação. Ela não contém métodos @Test.

Os usuários necessários aos cenários são preparados pelo UsuarioRepository, com senha codificada pelo PasswordEncoder real. Os tokens são obtidos pelos endpoints reais de login e confirmação de código. Alunos e vínculos são criados pelos próprios endpoints.

Controller, services de negócio, autenticação, autorização, repositories e banco são reais.

O H2 verifica a persistência JPA e os fluxos da aplicação. As migrations e os índices parciais específicos do PostgreSQL não são executados nem validados por esta suíte.

## 4. Matriz de endpoints cobertos

| # | Método | Rota | Controller | Caminho feliz (status) | Erro(s) (status) | Efeito verificado |
|---|---|---|---|---|---|---|
| 1 | POST | /api/alunos | AlunoController | 201 | 400 | Aluno persistido com os dados enviados; Location e Cache-Control |
| 2 | GET | /api/alunos | AlunoController | 200 | 401, 403 | Aluno criado aparece na consulta paginada; controle de acesso |
| 3 | GET | /api/alunos/{alunoId} | AlunoController | 200 | 404 | Dados retornados correspondem ao aluno criado |
| 4 | PUT | /api/alunos/{alunoId} | AlunoController | 200 | 409 | Alteração persistida; versão incrementada; versão antiga rejeitada |
| 5 | POST | /api/alunos/{alunoId}/inativar | AlunoController | 200 | Não contemplado nesta contribuição | Aluno permanece no banco com ativo=false |
| 6 | POST | /api/alunos/{alunoId}/responsaveis | AlunoResponsavelController | 201 | 400 | Vínculo persistido; segundo responsável atual rejeitado |
| 7 | GET | /api/alunos/{alunoId}/responsaveis | AlunoResponsavelController | 200 | Não contemplado nesta contribuição | Consulta retorna o vínculo atual do aluno |
| 8 | POST | /api/alunos/{alunoId}/responsaveis/{vinculoId}/encerrar | AlunoResponsavelController | 200 | Não contemplado nesta contribuição | Encerramento persistido; histórico preservado; vínculo deixa a consulta de atuais |

**Total de endpoints cobertos:** 8.

Os endpoints POST /api/auth/login e POST /api/auth/confirmar-login também são exercitados pelos helpers de autenticação. Não são contabilizados como cenários adicionais nem como controllers principais desta contribuição.

## 5. Cenários de integração implementados

| # | Endpoint | Cenário | Tipo | Status | Verificado além do status | Arquivo de teste | Método de teste |
|---|---|---|---|---|---|---|---|
| 1 | POST /api/alunos | Cadastro com dados válidos | Persistência | 201 | ID, nome, nascimento, ativo, versão, Location, Cache-Control e estado no banco | AlunoControllerIntegrationTest.java | cadastrar_comDadosValidos_deveRetornar201ComLocationEPersistir |
| 2 | GET /api/alunos | Direção consulta alunos ativos | Segurança | 200 | Aluno criado aparece na paginação com seus dados | AlunoControllerIntegrationTest.java | listar_comDirecao_deveRetornar200ComAlunoNaPagina |
| 3 | GET /api/alunos/{alunoId} | Consulta de aluno existente | Feliz | 200 | Corpo corresponde ao aluno criado | AlunoControllerIntegrationTest.java | visualizar_comAlunoExistente_deveRetornar200ComDados |
| 4 | PUT /api/alunos/{alunoId} | Atualização com versão atual | Persistência | 200 | Nome e nascimento alterados no banco; versão incrementada | AlunoControllerIntegrationTest.java | atualizar_comVersaoAtual_deveRetornar200EPersistirAlteracao |
| 5 | POST /api/alunos/{alunoId}/inativar | Inativação de aluno ativo | Persistência | 200 | Cadastro preservado no banco com ativo=false | AlunoControllerIntegrationTest.java | inativar_comAlunoAtivo_deveRetornar200EPreservarCadastro |
| 6 | POST /api/alunos | Nome vazio | Erro | 400 | Código e mensagem de erro; quantidade de alunos inalterada | AlunoControllerIntegrationTest.java | cadastrar_comNomeVazio_deveRetornar400SemPersistir |
| 7 | GET /api/alunos/{alunoId} | Identificador inexistente | Erro | 404 | Código ALUNO_NAO_ENCONTRADO e mensagem de erro | AlunoControllerIntegrationTest.java | visualizar_comIdInexistente_deveRetornar404ComMensagem |
| 8 | PUT /api/alunos/{alunoId} | Atualização com versão desatualizada | Erro | 409 | Código e mensagem de conflito; dados confirmados anteriormente preservados | AlunoControllerIntegrationTest.java | atualizar_comVersaoDesatualizada_deveRetornar409SemAlterar |
| 9 | GET /api/alunos | Requisição sem token | Segurança | 401 | Código e mensagem de erro; header WWW-Authenticate | AlunoControllerIntegrationTest.java | listar_semToken_deveRetornar401ComMensagem |
| 10 | GET /api/alunos | Professor autenticado sem permissão | Segurança | 403 | Código ACESSO_NEGADO e mensagem de erro | AlunoControllerIntegrationTest.java | listar_comProfessor_deveRetornar403ComMensagem |
| 11 | POST /api/alunos/{alunoId}/responsaveis | Vínculo de aluno com responsável | Persistência | 201 | Identificadores, criação, ausência de encerramento e registro no banco | AlunoResponsavelControllerIntegrationTest.java | vincular_comAlunoEResponsavel_deveRetornar201EPersistir |
| 12 | GET /api/alunos/{alunoId}/responsaveis | Criar aluno, vincular responsável e consultar relacionamento | Relacionamento | 200 | Lista contém o vínculo e os identificadores esperados | AlunoResponsavelControllerIntegrationTest.java | listarResponsaveis_aposVincular_deveRetornar200ComRelacionamento |
| 13 | POST /api/alunos/{alunoId}/responsaveis/{vinculoId}/encerrar | Encerramento do vínculo atual | Persistência | 200 | Histórico e data de criação preservados; encerramento gravado; consulta de atuais vazia | AlunoResponsavelControllerIntegrationTest.java | encerrar_comVinculoAtual_deveRetornar200EPreservarHistorico |
| 14 | POST /api/alunos/{alunoId}/responsaveis | Tentativa de segundo responsável atual para o mesmo aluno | Erro | 400 | Código e mensagem; vínculo original preservado; segundo vínculo não criado | AlunoResponsavelControllerIntegrationTest.java | vincular_comAlunoJaVinculado_deveRetornar400SemCriarSegundoVinculo |

Tipo: Feliz | Erro | Segurança | Persistência | Relacionamento

**Total de cenários de integração:** 14.

## 6. Arquivos criados ou alterados

| Arquivo (caminho completo) | Criado / Alterado | Qtd. de testes |
|---|---|---|
| src/test/java/com/crecheconecta/controller/BaseControllerIntegrationTest.java | Criado | 0 — classe abstrata de apoio |
| src/test/java/com/crecheconecta/controller/AlunoControllerIntegrationTest.java | Criado | 10 |
| src/test/java/com/crecheconecta/controller/AlunoResponsavelControllerIntegrationTest.java | Criado | 4 |
| src/test/resources/application-test.properties | Criado | Não se aplica — configuração |
| README-TestesIntegracao-Murilo-Backend.md | Criado | Não se aplica — documentação |

**Total de arquivos de teste:** 3, incluindo a base abstrata.  
**Total de testes de integração:** 14.

A classe inicialmente unificada foi substituída pelas classes separadas por controller. Não há duplicação dos cenários.

## 7. Como executar os testes

Na raiz do backend, executar no PowerShell:

```powershell
.\mvnw.cmd "-Dtest=AlunoControllerIntegrationTest,AlunoResponsavelControllerIntegrationTest" clean test
```

Para repetir sem limpar os arquivos gerados:

```powershell
.\mvnw.cmd "-Dtest=AlunoControllerIntegrationTest,AlunoResponsavelControllerIntegrationTest" test
```

Os testes utilizam porta aleatória e banco H2 em memória. Não exigem Docker nem PostgreSQL para os cenários desta contribuição.

A execução completa do repositório, sem seleção de classes, ainda precisa ser validada após a adequação dos testes antigos que possuem configuração própria de PostgreSQL local.

## 8. Evidências

- **Resultado da execução:** execução seletiva das duas classes concluída com BUILD SUCCESS, repetida com sucesso.

```text
AlunoControllerIntegrationTest:
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0

AlunoResponsavelControllerIntegrationTest:
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0

Total:
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0

BUILD SUCCESS
```

- **Link do CI (se houver):** não informado; evidências disponíveis nos relatórios locais em target/surefire-reports e no registro da execução.
- **Limite da evidência:** o resultado apresentado corresponde às duas classes selecionadas, sem comprovar a aprovação de todos os testes existentes no repositório.
- **Aplicação de desenvolvimento na porta 8080:** execução simultânea ainda não registrada como evidência.

## 9. Decisões e dificuldades

- **Segurança:** o GET /api/alunos é verificado sem token, retornando 401; com professor autenticado, retornando 403; e com direção autenticada, retornando 200. Os tokens são obtidos pelos endpoints reais de login e confirmação, com captura do código no envio de e-mail simulado.
- **Bugs encontrados pelos testes (se houver):** nenhum bug de produção identificado nas execuções apresentadas.
- **Dificuldades:** o arquivo application-test.properties foi inicialmente criado em src/main/resources com codificação incompatível com UTF-8, causando MalformedInputException durante o processamento de recursos do Maven. O arquivo foi colocado em src/test/resources e a configuração foi corrigida antes da execução bem-sucedida.
- **Organização:** configuração e helpers compartilhados foram extraídos para BaseControllerIntegrationTest, mantendo os cenários separados por controller.
- **Persistência:** não foi usado @Transactional na classe de teste, pois as chamadas HTTP são processadas em outra thread e as transações são concluídas pelos services.
- **Banco de teste:** Flyway permanece desativado no perfil test. A estrutura H2 é criada a partir das entidades JPA; não há validação das migrations ou das restrições específicas do PostgreSQL.
- **Suíte completa:** AtividadeAPIIntegracaoTest e CrecheconectaCamadasApplicationTests possuem configurações próprias de PostgreSQL local. A adequação desses testes e a validação da execução completa permanecem pendentes.
- **Fluxo de branches informado pelo projeto:** feature-turma → develop → feature/testes-integracao. Esse fluxo transfere as alterações para a branch de testes. A origem e o destino do PR acadêmico devem ser conferidos separadamente, pois o enunciado solicita a entrega da branch de testes para a principal.
- **Nome da branch:** o projeto utiliza feature/testes-integracao; o enunciado apresenta feat/testes-integracao. Essa diferença deve ser alinhada com o professor.

## 10. Checklist de entrega

- [x] Todos os testes passam com o comando da seção 7, mais de uma vez seguida
- [x] Todos os endpoints dos controllers escolhidos estão na matriz da seção 4
- [x] Cada cenário listado na seção 5 existe no código
- [x] Cada arquivo criado ou alterado está listado na seção 6
- [x] Mínimos da Seção 3.1 do enunciado atendidos (pelo menos 12 testes de integração)
- [x] Nenhum teste com @Disabled, sem asserção ou com Thread.sleep
- [x] Service e repository são reais nos testes de integração
- [ ] Professor adicionado como reviewer