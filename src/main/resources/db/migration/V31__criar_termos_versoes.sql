-- Termos de uso e política de privacidade versionados no banco (antes ficavam fixos no código).
-- Ciclo de vida: RASCUNHO -> VIGENTE -> ARQUIVADO. Só pode existir UMA versão vigente.
-- O aceite do usuário (aceites_termo.versao_termo) guarda o "identificador" da versão aceita.
CREATE TABLE termos_versoes (
    id UUID PRIMARY KEY,
    identificador VARCHAR(40) NOT NULL,
    termo_uso VARCHAR(20000) NOT NULL,
    politica_privacidade VARCHAR(20000) NOT NULL,
    status VARCHAR(20) NOT NULL,
    criado_por UUID,
    criado_em TIMESTAMPTZ NOT NULL,
    atualizado_em TIMESTAMPTZ NOT NULL,
    publicado_em TIMESTAMPTZ,
    versao BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_termos_versoes_identificador
        UNIQUE (identificador),
    CONSTRAINT fk_termos_versoes_criado_por
        FOREIGN KEY (criado_por)
            REFERENCES usuarios (id)
            ON DELETE RESTRICT,
    CONSTRAINT ck_termos_versoes_status
        CHECK (status IN ('RASCUNHO', 'VIGENTE', 'ARQUIVADO')),
    CONSTRAINT ck_termos_versoes_identificador
        CHECK (LENGTH(TRIM(identificador)) > 0),
    CONSTRAINT ck_termos_versoes_textos
        CHECK (LENGTH(TRIM(termo_uso)) > 0 AND LENGTH(TRIM(politica_privacidade)) > 0),
    -- Rascunho ainda não foi publicado; vigente e arquivado já foram.
    CONSTRAINT ck_termos_versoes_publicacao
        CHECK (
            (status = 'RASCUNHO' AND publicado_em IS NULL)
            OR (status <> 'RASCUNHO' AND publicado_em IS NOT NULL)
        )
);

-- Garante, no próprio banco, que existe no máximo uma versão vigente.
CREATE UNIQUE INDEX uk_termos_versoes_vigente
    ON termos_versoes (status)
    WHERE status = 'VIGENTE';

CREATE INDEX idx_termos_versoes_criado_em
    ON termos_versoes (criado_em DESC, id DESC);

-- O sistema não pode ficar sem termo: a versão que estava no código vira a vigente.
-- O texto foi revisado (acentos, serviço de e-mail Resend e versão do documento alinhada).
INSERT INTO termos_versoes (id, identificador, termo_uso, politica_privacidade, status, criado_por, criado_em, atualizado_em, publicado_em)
VALUES (
    'd0000000-0000-0000-0000-000000000001',
    '2026-09-26-v2',
    $termo$Termos de Uso do CrecheConecta

Descrição e Finalidade do Serviço
O CrecheConecta é uma plataforma digital desenvolvida para facilitar a comunicação e a troca de informações cotidianas entre instituições de educação infantil e os pais ou responsáveis legais das crianças.

Criação e Proteção da Conta
O acesso ao sistema é pessoal e intransferível. O responsável compromete-se a fornecer informações verdadeiras e exatas no momento do cadastro e a manter a confidencialidade de sua senha, que é rigorosamente criptografada pelo sistema. O usuário é integralmente responsável por todas as atividades realizadas sob o seu login.

Condutas Proibidas
É estritamente proibido o uso da plataforma para fins ilegais, não autorizados ou que violem os direitos de terceiros. O usuário não deve tentar acessar perfis de outros alunos ou elevar seus privilégios de acesso dentro do sistema.

Suporte e Encerramento da Conta
O serviço poderá passar por manutenções ou indisponibilidades temporárias. O responsável pode solicitar o encerramento da sua conta e o término do vínculo com a plataforma a qualquer momento, mediante solicitação via e-mail de suporte. Após a solicitação, os dados serão tratados conforme o plano de descarte estipulado na Política de Privacidade. Estes Termos não retiram quaisquer direitos legais garantidos aos titulares de dados.

Requisitos de Idade, Representação e Permissões
O uso e cadastro na plataforma são restritos a indivíduos maiores de 18 anos que sejam, comprovadamente, pais ou responsáveis legais das crianças matriculadas na instituição de ensino. O perfil de usuário fornecido limita-se às permissões de acesso da categoria "Responsável", garantindo a visualização exclusiva das informações e fichas de saúde apenas de seus próprios dependentes vinculados.

Propriedade Intelectual e Conteúdo Enviado
Todo o conteúdo estrutural, design, códigos e marcas do aplicativo são de propriedade intelectual exclusiva do CrecheConecta. Quaisquer textos, mensagens ou documentos enviados pelo usuário permanecem sob sua responsabilidade e titularidade, sendo utilizados pelo sistema apenas para cumprir as funcionalidades contratadas de comunicação com a creche.

Consequências do Uso Indevido e Legislação Aplicável
A violação de qualquer regra descrita nestes Termos, especialmente das condutas proibidas, poderá resultar no bloqueio temporário ou no encerramento definitivo da conta, sem prejuízo de outras sanções legais cabíveis. Estes Termos de Uso são regidos e interpretados de acordo com as leis da República Federativa do Brasil.

Data e Versão
Estes Termos poderão passar por atualizações periódicas para refletir melhorias no sistema ou mudanças legais. Alterações significativas serão previamente comunicadas aos usuários.
Versão do Documento: 2026-09-26-v2
Data da última atualização: 26 de setembro de 2026.$termo$,
    $politica$Política de Privacidade do CrecheConecta

1. Responsáveis pelo Tratamento
O CrecheConecta atua como provedor de tecnologia e Operador técnico dos dados. A instituição de ensino contratante atua como a Controladora, sendo a principal responsável pelas decisões, bases legais e gestão do tratamento das informações dos alunos e responsáveis. O canal oficial para contato é o e-mail crecheconecta@gmail.com.

2. Dados Pessoais Tratados, Finalidades e Hipóteses Legais
O sistema coleta apenas os dados estritamente necessários para o seu funcionamento:
- E-mail do Responsável: autenticação, 2FA e recuperação de senha (Execução de Contrato).
- Senha: acesso seguro à conta (Execução de Contrato).
- Nomes dos Responsáveis, Endereço e Telefone: identificação da família e contato (Execução de Contrato e Legítimo Interesse da instituição).
- Nome do Aluno e Dados da Ficha de Saúde: perfil e acompanhamento diário. O tratamento de dados sensíveis de saúde é fundamentado na Tutela da Saúde e na Proteção da Vida (Art. 11 da LGPD).

3. Compartilhamento e Serviços Externos
O sistema não vende ou comercializa dados pessoais. Ocorrerá o compartilhamento restrito do e-mail do responsável com o serviço externo de envio de e-mails Resend, cuja única finalidade é operacionalizar o disparo de e-mails transacionais.

4. Retenção, Descarte e Direitos dos Titulares
Os dados serão mantidos enquanto a conta estiver ativa ou até a conclusão do vínculo com a creche. Conforme os Artigos 15, 16 e 18 da LGPD (Lei nº 13.709/2018), o usuário possui, entre outros, os direitos de: confirmação do tratamento e acesso aos dados; correção de dados incompletos ou desatualizados; eliminação, anonimização ou bloqueio dos dados. Para exercer a exclusão, o responsável deve enviar solicitação ao e-mail de atendimento; a equipe executará manualmente a anonimização irreversível ou a exclusão no banco de dados.

5. Segurança e Incidentes
O sistema utiliza técnicas de hash forte para a criptografia das senhas de acesso. O acesso aos demais dados cadastrais e às fichas de saúde é protegido por controles rígidos de autorização, restritos a perfis autenticados e autorizados. Em caso de incidentes de segurança com risco relevante, os titulares e as autoridades competentes serão comunicados.$politica$,
    'VIGENTE',
    NULL,
    '2026-09-26 00:00:00-03',
    '2026-09-26 00:00:00-03',
    '2026-09-26 00:00:00-03'
);
