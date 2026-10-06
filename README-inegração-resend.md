# CrecheConecta — Autenticação e integração com Resend

## 1. Objetivo

Este documento apresenta o projeto lógico e a implementação da integração
do CrecheConecta com a API externa Resend.

A integração permite enviar e-mails transacionais utilizados em:

- Confirmação de acesso após a validação de e-mail e senha.
- Recuperação de senha por código de verificação.
- Notificação de alteração de senha.

O Resend é responsável pelo envio dos e-mails. A geração dos códigos,
a validação das credenciais, o controle de tentativas e a gestão das
sessões são responsabilidades do back-end do CrecheConecta.

## 2. Escopo da implementação

O fluxo implementado permite:

1. Informar e-mail e senha.
2. Receber um código por e-mail.
3. Confirmar o código e obter uma sessão.
4. Consultar os dados do usuário autenticado.
5. Encerrar a sessão.
6. Solicitar recuperação de senha.
7. Confirmar o código de recuperação.
8. Definir uma nova senha.
9. Revogar as sessões anteriores e enviar um aviso da alteração.

O front-end disponibiliza as telas de autenticação e recuperação,
além de uma home com identificação do usuário e logout.

Os perfis previstos são `DIRECAO`, `PROFESSOR` e `PAIS`.
O perfil é carregado na autenticação. As permissões específicas das
funcionalidades.

## 3. Tecnologias

| Área | Tecnologia |
|---|---|
| Back-end | Java 21 e Spring Boot |
| Segurança | Spring Security |
| Persistência | Spring Data JPA e PostgreSQL |
| Migrações | Flyway |
| Cliente HTTP externo | Spring RestClient |
| API externa | Resend |
| Front-end | Angular 21 e TypeScript |
| Estilização | Tailwind CSS 4 |

## 4. Projeto lógico

### 4.1. Organização dos componentes

O back-end utiliza arquitetura em camadas:

- **Controller:** recebe as requisições HTTP e valida os DTOs.
- **Service:** executa as regras dos fluxos de autenticação e recuperação.
- **Repository:** acessa os registros no PostgreSQL.
- **Entity:** representa usuários, ações de verificação e sessões.
- **Configuration:** configura segurança, cliente HTTP e executor.
- **Exception:** padroniza as respostas de erro.

O `EmailService` concentra a integração com o provedor externo.
O front-end não acessa o Resend diretamente.

### 4.2. Principais classes

| Classe | Responsabilidade |
|---|---|
| `ResendConfig` | Configurar URL, autenticação e timeouts do cliente HTTP |
| `EmailService` | Montar mensagens, enviar e tratar respostas do Resend |
| `AuthService` | Validar credenciais e iniciar a confirmação de login |
| `ConfirmacaoLoginService` | Validar o código e criar a sessão |
| `RecuperacaoSenhaService` | Processar a solicitação de recuperação |
| `ConfirmacaoRecuperacaoService` | Validar o código e emitir autorização de redefinição |
| `RedefinicaoSenhaService` | Alterar a senha e revogar sessões |
| `SessaoService` | Autenticar tokens e executar logout |
| `SegredoVerificacaoService` | Gerar códigos e proteger segredos com HMAC |
| `TokenSessaoService` | Gerar tokens de sessão e calcular seus hashes |
| `RecuperacaoExecutorConfig` | Configurar processamento em segundo plano |
| `ApiExceptionHandler` | Padronizar erros apresentados pela API |


## 5. Fluxo de login

1. O usuário informa seu e-mail em `/api/auth/login`.
2. O usuário informa o código de dois fatores em `/api/auth/confirmar-login`.
3. É verificado a validação do usuário `/api/auth/me`.
4. O usuário pode sair da sessão `/api/auth/logout`.

## 6. Fluxo de recuperação

1. O usuário informa seu e-mail em `/api/auth/esqueci-senha`.
2. A API aceita o processamento e retorna uma mensagem genérica.
3. O processamento em segundo plano verifica a conta e os limites de envio.
4. Para uma conta apta, cria uma ação e envia o código pelo Resend.
5. O usuário confirma o código em `/api/auth/confirmar-recuperacao`.
6. A API consome o código e cria uma autorização temporária de redefinição.
7. O usuário envia essa autorização e a nova senha em `/api/auth/redefinir-senha`.
8. Na mesma transação, a API:
    - Atualiza o hash da senha.
    - Zera as tentativas de login e remove o bloqueio temporário.
    - Consome a autorização.
    - Invalida outras ações pendentes.
    - Revoga as sessões anteriores.
9. Após a transação, envia o aviso de senha alterada.
10. O usuário realiza um novo login.

## 7. Contrato com a API externa

### Requisição

```http
POST https://api.resend.com/emails
Authorization: Bearer <RESEND_API_KEY>
Content-Type: application/json
Idempotency-Key: <identificador-da-operacao>
```

Exemplo ilustrativo de corpo:

```json
{
  "from": "CrecheConecta <onboarding@resend.dev>",
  "to": ["destinatario@example.com"],
  "subject": "CrecheConecta - Código de acesso",
  "text": "Mensagem com o código temporário de verificação."
}
```

A aplicação utiliza o campo `id` da resposta para identificar o envio
nos logs operacionais.

### Chaves de idempotência

| Mensagem | Formato |
|---|---|
| Código de login ou recuperação | `acao-verificacao/<acaoId>` |
| Aviso de senha alterada | `senha-alterada/<redefinicaoId>` |

A chave permite ao provedor reconhecer repetições da mesma operação
conforme suas regras de idempotência. Ela não implementa retentativas
automáticas na aplicação.

### Configuração HTTP

- Timeout de conexão: 5 segundos.
- Timeout de leitura: 10 segundos.
- Redirecionamentos automáticos desabilitados.
- Autenticação da integração por API key no cabeçalho Bearer.
- Comunicação com o Resend por HTTPS.

## 8. Segurança

### Senhas e segredos

- Senhas são armazenadas com BCrypt, custo 12.
- BCrypt é um hash de senha, não uma criptografia reversível.
- A comparação utiliza `PasswordEncoder.matches`.
- A nova senha deve ter pelo menos 12 caracteres e no máximo
  72 bytes em UTF-8.
- Códigos são gerados com `SecureRandom`.
- Códigos e autorizações de redefinição são protegidos no banco
  com HMAC-SHA256, vinculados ao usuário, à ação e à finalidade.
- O banco armazena o hash SHA-256 dos tokens de sessão.
- Senhas e tokens de sessão não são enviados ao Resend.
- O Resend recebe o endereço do destinatário e o conteúdo da
  mensagem, incluindo o código temporário quando aplicável.

### Prazos e limites

| Controle | Configuração atual |
|---|---|
| Validade do código | 5 minutos |
| Validade da autorização de redefinição | 5 minutos |
| Validade da sessão | 15 minutos |
| Falhas de senha para bloqueio | 5 |
| Duração do bloqueio de login | 15 minutos |
| Tentativas incorretas por ação de código | Até 5 |
| Intervalo mínimo entre emissões por conta e finalidade | 60 segundos |
| Ações de recuperação por conta | Até 5 por hora |

A confirmação de códigos também considera as tentativas acumuladas
nas ações da mesma finalidade criadas nos últimos 15 minutos.
Ao atingir 10 erros nesse conjunto, a confirmação é recusada.

As ações são consumidas ou invalidadas para impedir reutilização.
A atualização de senha e a revogação das sessões ocorrem na mesma transação.

### Front-end e acesso

- A chave do Resend permanece no back-end.
- O token de sessão fica em memória no Angular.
- Atualizar a página exige novo login.
- O back-end valida as sessões; o guard do Angular controla a navegação.
- O projeto utiliza tokens opacos, não JWT.
- A confirmação por e-mail constitui uma etapa adicional de verificação;
  este documento não atribui certificação ou nível formal de MFA ao fluxo.

## 9. Configuração do ambiente

Configure as seguintes variáveis no processo do back-end:

| Variável | Finalidade |
|---|---|
| `RESEND_API_KEY` | Chave de acesso ao Resend |
| `RESEND_FROM` | Remetente utilizado nos e-mails |
| `AUTH_HMAC_KEY_BASE64` | Chave aleatória de pelo menos 32 bytes, codificada em Base64 |
| `SPRING_PROFILES_ACTIVE` | Perfil do Spring; `dev` no ambiente local |

O banco PostgreSQL também precisa estar configurado.

Mapeamentos necessários em `application.properties`:

```properties
app.email.resend.api-key=${RESEND_API_KEY}
app.email.resend.remetente=${RESEND_FROM}
app.seguranca.hmac-chave-base64=${AUTH_HMAC_KEY_BASE64}
```

Exemplo de remetente para o ambiente de testes:

```text
CrecheConecta <onboarding@resend.dev>
```

Não incluir valores reais de chaves, senhas ou tokens no repositório.

A chave HMAC deve permanecer estável entre reinicializações.
Sua alteração impede validar ações pendentes protegidas com a chave anterior.

### Restrição do domínio de testes

O remetente `onboarding@resend.dev` possui restrições de destinatário.
No ambiente utilizado, os testes foram realizados com o e-mail
autorizado da conta Resend.

Para enviar aos demais usuários cadastrados, será necessário verificar
um domínio próprio e configurar o remetente correspondente.
Ampliar as permissões da API key não remove essa restrição.

### Execução local

No diretório do back-end, com as variáveis configuradas:

```powershell
.\mvnw.cmd spring-boot:run
```

No diretório do front-end:

```powershell
npm install
npm start -- --proxy-config proxy.config.json
```

Endereços locais:

- Front-end: `http://localhost:4200`
- Back-end: `http://localhost:8080`

O proxy Angular encaminha `/api/**` ao back-end. Essa configuração
é de desenvolvimento e não substitui a configuração de publicação.

## 10. Endpoints do CrecheConecta

| Método | Endpoint | Dados principais | Sucesso |
|---|---|---|---|
| POST | `/api/auth/login` | `email`, `senha` | 200 |
| POST | `/api/auth/confirmar-login` | `acaoId`, `codigo` | 200 |
| GET | `/api/auth/me` | Bearer Token | 200 |
| POST | `/api/auth/logout` | Bearer Token | 204 |
| POST | `/api/auth/esqueci-senha` | `email` | 202 |
| POST | `/api/auth/confirmar-recuperacao` | `acaoId`, `codigo` | 200 |
| POST | `/api/auth/redefinir-senha` | `redefinicaoId`, `tokenRedefinicao`, `novaSenha`, `confirmacaoSenha` | 204 |

A resposta de confirmação do login contém o token de sessão.
A confirmação da recuperação retorna uma autorização exclusiva
para redefinir a senha.

## 11. Tratamento de falhas

| Situação | Comportamento |
|---|---|
| Falha no envio do código de login | Invalida a ação e retorna erro 503 |
| Falha no processamento assíncrono de recuperação | Registra a falha; o 202 já enviado não é alterado |
| Falha ao enviar código de recuperação após criar a ação | Invalida a ação |
| Falha no aviso de senha alterada | Registra a falha e preserva a alteração concluída |
| Código inválido, expirado ou reutilizado | Erro 400 |
| Limite de tentativas atingido | Erro 429 |
| Credenciais inválidas | Erro 401 |
| Sessão inválida ou revogada | Erro 401 |

Os logs do envio registram referências da operação, identificador
retornado pelo Resend e informações técnicas de falha.

Os métodos de envio não registram senhas, códigos, tokens ou o corpo
completo das mensagens.

Esses logs operacionais não constituem, por si só, uma trilha completa
de auditoria das ações dos usuários.

## 12. Validação

Os fluxos de autenticação e recuperação foram exercitados manualmente
durante o desenvolvimento, utilizando Postman e o front-end Angular.

Essa validação não equivale a uma suíte automatizada de testes.

### Roteiro de reprodução

1. Utilizar uma conta ativa com destinatário autorizado no Resend.
2. Informar e-mail e senha no login.
3. Confirmar o recebimento do código.
4. Confirmar o código e consultar `/api/auth/me`.
5. Executar logout e verificar a rejeição do token anterior.
6. Solicitar recuperação de senha.
7. Confirmar o código de recuperação.
8. Definir uma nova senha.
9. Verificar o aviso de alteração.
10. Confirmar que a senha antiga é recusada e a nova permite login.

## 14. Referências

- [Resend — envio de e-mail](https://resend.com/docs/api-reference/emails/send-email)
- [Resend — idempotência](https://resend.com/docs/dashboard/emails/idempotency-keys)
- [Resend — gerenciamento de domínios](https://resend.com/docs/dashboard/domains/introduction)
- [OWASP — recuperação de senha](https://cheatsheetseries.owasp.org/cheatsheets/Forgot_Password_Cheat_Sheet.html)