package com.crecheconecta.dto;

import com.crecheconecta.entity.MensagemSolicitacaoMaterial;
import com.crecheconecta.entity.Perfil;
import com.crecheconecta.entity.Usuario;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SolicitacaoMaterialDetalheResponse(
        SolicitacaoMaterialResponse solicitacao,
        List<Mensagem> mensagens
) {

    public record Mensagem(
            UUID id,
            UUID autorId,
            String autorNome,
            Perfil autorPerfil,
            String conteudo,
            Instant criadoEm
    ) {

        public static Mensagem de(MensagemSolicitacaoMaterial mensagem, Usuario autor) {

            return new Mensagem(
                    mensagem.getId(),
                    mensagem.getAutorId(),
                    autor.getNome(),
                    autor.getPerfil(),
                    mensagem.getConteudo(),
                    mensagem.getCriadoEm()
            );
        }
    }
}
