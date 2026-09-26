# CrecheConecta — Back-end

API do CrecheConecta, uma plataforma para centralizar a comunicação entre pais, professores e direção de creches e escolas de educação infantil.

O projeto está sendo desenvolvido como Projeto Final de Curso (PFC) de Engenharia de Software da Universidade de Mogi das Cruzes (UMC).

## Objetivo

Gerenciar os dados e as regras de negócio da plataforma, permitindo o registro da rotina das crianças, a organização das informações escolares e a comunicação entre famílias e equipe escolar.

## Tecnologias

- Java e Spring Boot
- Spring Security para autenticação e autorização
- Spring Data JPA / Hibernate para persistência
- PostgreSQL como banco de dados relacional
- Flyway para versionamento e migrações do banco de dados
- WebSockets para comunicação em tempo real
- JUnit e Mockito para testes unitários

A comunicação com o front-end será feita por uma API REST, com dados em JSON.

## Banco de dados

O PostgreSQL será utilizado para armazenar os dados estruturados da aplicação.

As alterações na estrutura do banco serão controladas pelo Flyway, por meio de migrações versionadas junto ao código-fonte. Isso permitirá acompanhar a evolução do banco e aplicar as alterações de forma consistente nos diferentes ambientes.

## Integrações previstas

- **ViaCEP:** consulta de CEP para preenchimento de endereços.
- **Google Calendar:** sincronização de eventos e reuniões escolares.
- **Resend:** envio de e-mails de recuperação de senha.

## Front-end

[Repositório do front-end](https://github.com/MuriloN0/CrecheConecta---Front-end)

## Organização das branches

- `main`: versão estável do projeto.
- `develop`: integração das funcionalidades em desenvolvimento.

## Status

Em desenvolvimento. As instruções de configuração do ambiente, execução e acesso à API serão adicionadas conforme a implementação avançar.


## Como executar localmente

### Pré-requisitos

- Git.
- JDK 21, com a variável `JAVA_HOME` configurada.
- PostgreSQL instalado e em execução.
- Maven.

### 1. Clonar o repositório

```bash
git clone https://github.com/MuriloN0/CrecheConecta---Back-end.git
cd CrecheConecta---Back-end
git switch develop
```

### 2. Criar o banco de dados

No pgAdmin ou no terminal do PostgreSQL, execute:

```sql
CREATE DATABASE crecheconecta;
```

O banco precisa existir antes de iniciar a aplicação. As tabelas serão criadas pelas migrações disponíveis no projeto.

### 3. Configurar a conexão

Em `src/main/resources/application.properties`, configure a conexão usando variáveis de ambiente:

```properties
spring.datasource.url=${SPRING_DATASOURCE_URL}
spring.datasource.username=${SPRING_DATASOURCE_USERNAME}
spring.datasource.password=${SPRING_DATASOURCE_PASSWORD}

spring.jpa.hibernate.ddl-auto=validate
spring.flyway.enabled=true
spring.flyway.locations=classpath:db/migration
```

### 4. Executar a aplicação

Utilize o Maven instalado:

```bash
mvn spring-boot:run
```

Por padrão, a API será iniciada em `http://localhost:8080`, salvo configuração diferente no projeto.

O acesso aos endpoints depende das rotas implementadas e das regras do Spring Security. A API não possui necessariamente uma página inicial no navegador.

## Equipe

- Gabriel Belim Longhi
- Iago Lucas Fernandes de Faria
- Murilo Novaes de Oliveira

**Instituição:** Universidade de Mogi das Cruzes (UMC)  
**Curso:** Engenharia de Software  
**Ano:** 2026

