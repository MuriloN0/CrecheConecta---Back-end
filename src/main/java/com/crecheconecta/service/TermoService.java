package com.crecheconecta.service;

import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.repository.AceiteTermoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TermoService {

    public static final String VERSAO_ATUAL = "2026-09-26-v2";

    private static final String TERMO_USO =
            "Termos de Uso do CrecheConecta\n" +
                    "\n" +
                    "Descrição e Finalidade do Serviço\n" +
                    "O CrecheConecta é uma plataforma digital desenvolvida para facilitar a comunicação e a troca de informações cotidianas entre instituições de educação infantil e os pais ou responsáveis legais das crianças.\n" +
                    "\n" +
                    "Criação e Proteção da Conta\n" +
                    "O acesso ao sistema é pessoal e intransferível. O responsável compromete-se a fornecer informações verdadeiras e exatas no momento do cadastro e a manter a confidencialidade de sua senha, que é rigorosamente criptografada pelo sistema. O usuário é integralmente responsável por todas as atividades realizadas sob o seu login.\n" +
                    "\n" +
                    "Condutas Proibidas\n" +
                    "É estritamente proibido o uso da plataforma para fins ilegais, não autorizados ou que violem os direitos de terceiros. O usuário não deve tentar acessar perfis de outros alunos ou elevar seus privilégios de acesso dentro do sistema.\n" +
                    "\n" +
                    "Suporte e Encerramento da Conta\n" +
                    "O serviço poderá passar por manutenções ou indisponibilidades temporárias. O responsável pode solicitar o encerramento da sua conta e o término do vínculo com a plataforma a qualquer momento, mediante solicitação via e-mail de suporte. Após a solicitação, os dados serão tratados conforme o plano de descarte estipulado na Política de Privacidade. Estes Termos não retiram quaisquer direitos legais garantidos aos titulares de dados.\n" +
                    "\n" +
                    "Requisitos de Idade, Representação e Permissões\n" +
                    "O uso e cadastro na plataforma são restritos a indivíduos maiores de 18 anos que sejam, comprovadamente, pais ou responsáveis legais das crianças matriculadas na instituição de ensino. O perfil de usuário fornecido limita-se às permissões de acesso da categoria \"Responsável\", garantindo a visualização exclusiva das informações e fichas de saúde apenas de seus próprios dependentes vinculados.\n" +
                    "\n" +
                    "Propriedade Intelectual e Conteúdo Enviado\n" +
                    "Todo o conteúdo estrutural, design, códigos e marcas do aplicativo são de propriedade intelectual exclusiva do CrecheConecta. Quaisquer textos, mensagens ou documentos enviados pelo usuário permanecem sob sua responsabilidade e titularidade, sendo utilizados pelo sistema apenas para cumprir as funcionalidades contratadas de comunicação com a creche.\n" +
                    "\n" +
                    "Consequências do Uso Indevido e Legislação Aplicável\n" +
                    "A violação de qualquer regra descrita nestes Termos, especialmente das condutas proibidas, poderá resultar no bloqueio temporário ou no encerramento definitivo da conta, sem prejuízo de outras sanções legais cabíveis. Estes Termos de Uso são regidos e interpretados de acordo com as leis da República Federativa do Brasil.\n" +
                    "\n" +
                    "Data e Versão\n" +
                    "Estes Termos poderão passar por atualizações periódicas para refletir melhorias no sistema ou mudanças legais. Alterações significativas serão previamente comunicadas aos usuários.\n" +
                    "Versão do Documento: 1.0\n" +
                    "Data da última atualização: 26 de setembro de 2026.";

    private static final String POLITICA_PRIVACIDADE =
            "Politica de Privacidade do CrecheConecta\n\n"
                    + "1. Responsaveis pelo Tratamento\n"
                    + "O CrecheConecta atua como provedor de tecnologia e Operador tecnico dos dados. A "
                    + "instituicao de ensino contratante atua como a Controladora, sendo a principal "
                    + "responsavel pelas decisoes, bases legais e gestao do tratamento das informacoes dos "
                    + "alunos e responsaveis. O canal oficial para contato e o e-mail crecheconecta@gmail.com.\n\n"
                    + "2. Dados Pessoais Tratados, Finalidades e Hipoteses Legais\n"
                    + "O sistema coleta apenas os dados estritamente necessarios para o seu funcionamento:\n"
                    + "- E-mail do Responsavel: autenticacao, 2FA e recuperacao de senha (Execucao de Contrato).\n"
                    + "- Senha: acesso seguro a conta (Execucao de Contrato).\n"
                    + "- Nomes dos Responsaveis, Endereco e Telefone: identificacao da familia e contato "
                    + "(Execucao de Contrato e Legitimo Interesse da instituicao).\n"
                    + "- Nome do Aluno e Dados da Ficha de Saude: perfil e acompanhamento diario. O "
                    + "tratamento de dados sensiveis de saude e fundamentado na Tutela da Saude e na "
                    + "Protecao da Vida (Art. 11 da LGPD).\n\n"
                    + "3. Compartilhamento e Servicos Externos\n"
                    + "O sistema nao vende ou comercializa dados pessoais. Ocorrera o compartilhamento "
                    + "restrito do e-mail do responsavel com a API externa Sender, cuja unica finalidade e "
                    + "operacionalizar o disparo de e-mails transacionais.\n\n"
                    + "4. Retencao, Descarte e Direitos dos Titulares\n"
                    + "Os dados serao mantidos enquanto a conta estiver ativa ou ate a conclusao do vinculo "
                    + "com a creche. Conforme os Artigos 15, 16 e 18 da LGPD (Lei no 13.709/2018), o usuario "
                    + "possui, entre outros, os direitos de: confirmacao do tratamento e acesso aos dados; "
                    + "correcao de dados incompletos ou desatualizados; eliminacao, anonimizacao ou bloqueio "
                    + "dos dados. Para exercer a exclusao, o responsavel deve enviar solicitacao ao e-mail "
                    + "de atendimento; a equipe executara manualmente a anonimizacao irreversivel ou a "
                    + "exclusao no banco de dados.\n\n"
                    + "5. Seguranca e Incidentes\n"
                    + "O sistema utiliza tecnicas de hash forte para a criptografia das senhas de acesso. "
                    + "O acesso aos demais dados cadastrais e as fichas de saude e protegido por controles "
                    + "rigidos de autorizacao, restritos a perfis autenticados e autorizados. Em caso de "
                    + "incidentes de seguranca com risco relevante, os titulares e as autoridades "
                    + "competentes serao comunicados.";

    private final AceiteTermoRepository repository;
    private final AuditoriaService auditoria;

    public TermoService(AceiteTermoRepository repository, AuditoriaService auditoria) {
        this.repository = repository;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public String versaoAtual() {
        return VERSAO_ATUAL;
    }

    @Transactional(readOnly = true)
    public String termoUso() {
        return TERMO_USO;
    }

    @Transactional(readOnly = true)
    public String politicaPrivacidade() {
        return POLITICA_PRIVACIDADE;
    }

    @Transactional(readOnly = true)
    public boolean precisaAceitar(UUID usuarioId) {
        return repository
                .findFirstByUsuarioIdOrderByDataAceiteDesc(usuarioId)
                .map(aceite -> !aceite.getVersaoTermo().equals(VERSAO_ATUAL))
                .orElse(true);
    }
    public void registrarAceite(UUID usuarioId) {
        AceiteTermo aceite = new AceiteTermo();
        aceite.setUsuarioId(usuarioId);
        aceite.setVersaoTermo(VERSAO_ATUAL);
        repository.save(aceite);
        auditoria.aposCommit("TERMO_ACEITO", usuarioId, null);
    }
}