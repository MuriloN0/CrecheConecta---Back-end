# CrecheConecta — Back-end

API REST do CrecheConecta, plataforma de gestão e comunicação entre famílias, professores e direção de creches. O projeto é um Trabalho de Conclusão de Curso de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

[Repositório do front-end](https://github.com/MuriloN0/CrecheConecta---Front-end)

## Funcionalidades

- Autenticação em duas etapas, com e-mail e senha seguidos de código enviado pelo Resend. A quinta falha de senha bloqueia o login da conta por cinco minutos.
- Convites para cadastro e gestão de alunos, professores, responsáveis, turmas, matrículas e vínculos. O cadastro de aluno por convite exige quatro meses completos como regra de negócio do CrecheConecta.
- Chamada, diário de rotina, observações, informações relevantes de saúde, Mala Virtual e atividades do dia e para casa. As rotas paginadas da Mala Virtual e das atividades retornam até cinco registros por página.
- Comunicados, notificações, conversas e solicitações de materiais. A interface atualiza as conversas por consultas REST periódicas. Mensagens de conversa direta sem favorito deixam de aparecer após 168 horas do envio e são removidas pela limpeza periódica. O favorito de qualquer participante preserva a mensagem.
- Versionamento e aceite de termos, exportação em JSON dos dados do responsável e dos alunos vinculados, inativação de conta, anonimização e auditoria.

## Tecnologias e estrutura

Java 21, Spring Boot 4.1.1, Spring Security, Spring Data JPA, Hibernate, PostgreSQL, Flyway e Maven. O Resend envia e-mails transacionais. Os testes usam JUnit, Mockito e H2.

Os controladores recebem requisições HTTP e retornam DTOs. Os serviços aplicam as regras de negócio, as verificações de acesso e os limites das transações. Os repositórios concentram o acesso aos dados com JPA ou JDBC. As migrações estão em [src/main/resources/db/migration](src/main/resources/db/migration).

## Executar localmente

### Pré-requisitos

Git, JDK 21 e PostgreSQL em execução. O Maven Wrapper incluído no repositório pode ser usado para compilar e executar o projeto. Para testar o login real e os convites, configure uma conta Resend com remetente autorizado.

### 1. Clonar e preparar o banco

```bash
git clone https://github.com/MuriloN0/CrecheConecta---Back-end.git
cd CrecheConecta---Back-end
```

No PostgreSQL, crie um banco de desenvolvimento, por exemplo `crecheconecta`, e use um usuário com permissões para aplicar as migrações nesse banco. O Flyway cria e atualiza o esquema na inicialização. O Hibernate valida o esquema resultante.

### 2. Configurar o ambiente

Defina as variáveis no terminal ou na configuração de execução da IDE.

| Variável | Valor esperado |
| --- | --- |
| `SPRING_DATASOURCE_URL` | URL JDBC do banco, por exemplo `jdbc:postgresql://localhost:5432/crecheconecta`. |
| `SPRING_DATASOURCE_USERNAME` | Usuário do PostgreSQL com acesso ao banco. |
| `SPRING_DATASOURCE_PASSWORD` | Senha desse usuário. |
| `AUTH_HMAC_KEY_BASE64` | Chave HMAC aleatória em Base64 com pelo menos 32 bytes decodificados. |
| `SAUDE_CRIPTOGRAFIA_CHAVE` | Chave AES em Base64. Use 32 bytes aleatórios para AES-256. |
| `RESEND_API_KEY` | Chave da API do Resend. |
| `RESEND_FROM` | Endereço remetente autorizado no Resend. |

Use chaves distintas por finalidade e ambiente. Mantenha a chave AES do ambiente entre reinicializações para preservar a leitura dos dados criptografados. Guarde as credenciais e as chaves fora do Git.

Para outro endereço do front-end, ajuste `APP_CORS_ALLOWED_ORIGINS` e as URLs de convite `CADASTRO_URL` e `CADASTRO_DIRECAO_URL` conforme o ambiente. Os padrões de desenvolvimento apontam para a porta 4200.

### 3. Iniciar a API

No PowerShell, na raiz do repositório:

```powershell
.\mvnw.cmd spring-boot:run
```

No Linux ou macOS:

```bash
./mvnw spring-boot:run
```

A API local usa a porta 8080 por padrão. Para entrar no portal, use uma conta de teste ativa. Ao partir de um banco vazio, a primeira conta de direção precisa ser provisionada no ambiente antes de enviar convites pelo sistema. Use uma senha armazenada como hash BCrypt e um e-mail que possa receber o código de verificação.

### 4. Empacotar e rodar o JAR

No PowerShell:

```powershell
.\mvnw.cmd clean package
java -jar .\target\crecheconecta-0.0.1-SNAPSHOT.jar
```

O comando `java -jar` executa o arquivo já compilado. Para usar o [Dockerfile](Dockerfile), gere a imagem novamente após mudanças no código. O contêiner usa a porta 10000 por padrão e recebe a conexão com PostgreSQL e as demais configurações por variáveis de ambiente.

## API e controle de acesso

O login começa em `POST /api/auth/login`. Após receber o código por e-mail, o cliente confirma o acesso em `POST /api/auth/confirmar-login` e usa o token de sessão nas requisições protegidas. O servidor verifica os perfis `DIRECAO`, `PROFESSOR` e `RESPONSAVEL` e os vínculos com turmas ou alunos, conforme a operação.

Exemplos de consultas paginadas:

| Rota | Uso |
| --- | --- |
| `GET /api/atividades/paginadas` | Atividades com parâmetro `pagina` e filtro opcional `tipo`. O cabeçalho `turmaUUID` pode selecionar a turma. |
| `GET /api/turmas/{turmaId}/malas/paginadas` | Mala Virtual da turma para professor vinculado. |
| `GET /api/meus-filhos/{alunoId}/mala?pagina=0` | Mala Virtual para responsável vinculado. |
| `GET /api/alunos/disponiveis` | Alunos ativos sem matrícula vigente, com busca por nome para a direção. |
| `GET /api/professores/disponiveis` | Professores ativos e aprovados sem vínculo vigente com a turma informada em `turmaId`, com busca por nome para a direção. |

A primeira página tem índice zero. Para baixar a exportação de uma conta de responsável, o front-end usa `GET /api/meus-dados`.

Na hospedagem com o proxy de login da Vercel, configure `APP_AUTH_LOGIN_PROXY_REQUIRED=true` e `APP_AUTH_LOGIN_PROXY_SECRET` com o mesmo segredo de 64 caracteres hexadecimais da função. Na execução local com `npm start`, o proxy de desenvolvimento encaminha as chamadas diretamente para a API.

## Testes e integração contínua

No PowerShell:

```powershell
.\mvnw.cmd test
```

Em Bash, use `./mvnw test`. A suíte inclui testes unitários e testes de integração com H2 em memória. Os testes não exigem o PostgreSQL nem as credenciais do Resend do ambiente de desenvolvimento. Os relatórios do Maven Surefire ficam em `target/surefire-reports`.

O GitHub Actions roda a suíte com Java 21 em pushes para `main` e `develop` e em pull requests direcionados a essas branches.

## Organização das branches

- `main` mantém a versão estável.
- `develop` concentra a integração das mudanças.

## Equipe

- Gabriel Belim Longhi
- Iago Lucas Fernandes de Faria
- Murilo Novaes de Oliveira

Universidade de Mogi das Cruzes (UMC) · Engenharia de Software · 2026
