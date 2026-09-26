package com.crecheconecta.service;

import com.crecheconecta.entity.AceiteTermo;
import com.crecheconecta.repository.AceiteTermoRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TermoService {

    public static final String VERSAO_ATUAL = "2026-09-25-v1";

    private static final String TERMO_USO =
            "Termos de Uso do CrecheConecta\n\n"
                    + "1. Descricao e Finalidade do Servico\n"
                    + "O CrecheConecta e uma plataforma digital desenvolvida para facilitar a comunicacao "
                    + "e a troca de informacoes cotidianas entre instituicoes de educacao infantil e os "
                    + "pais ou responsaveis legais das criancas.\n\n"
                    + "2. Criacao e Protecao da Conta\n"
                    + "O acesso ao sistema e pessoal e intransferivel. O responsavel compromete-se a "
                    + "fornecer informacoes verdadeiras e exatas no momento do cadastro e a manter a "
                    + "confidencialidade de sua senha, que e rigorosamente criptografada pelo sistema. "
                    + "O usuario e integralmente responsavel por todas as atividades realizadas sob o seu login.\n\n"
                    + "3. Condutas Proibidas\n"
                    + "E estritamente proibido o uso da plataforma para fins ilegais, nao autorizados ou "
                    + "que violem os direitos de terceiros. O usuario nao deve tentar acessar perfis de "
                    + "outros alunos ou elevar seus privilegios de acesso dentro do sistema.\n\n"
                    + "4. Suporte e Encerramento da Conta\n"
                    + "O servico podera passar por manutencoes ou indisponibilidades temporarias. O "
                    + "responsavel pode solicitar o encerramento da sua conta e o termino do vinculo com a "
                    + "plataforma a qualquer momento, mediante solicitacao via e-mail de suporte. Apos a "
                    + "solicitacao, os dados serao tratados conforme o plano de descarte estipulado na "
                    + "Politica de Privacidade. Estes Termos nao retiram quaisquer direitos legais "
                    + "garantidos aos titulares de dados.";

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