package tn.esprit.backend.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.IProjetService;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjetServiceImpl implements IProjetService {

    private final ProjetRepository projetRepository;

    @Override
    public Projet addProjet(Projet projet) {
        return projetRepository.save(projet);
    }

    @Override
    public Projet updateProjet(Projet projet) {
        return projetRepository.save(projet);
    }

    @Override
    public void deleteProjet(Long id) {
        projetRepository.deleteById(id);
    }

    @Override
    public Projet getProjetById(Long id) {
        return projetRepository.findById(id).orElse(null);
    }

    @Override
    public List<Projet> getAllProjets() {
        return projetRepository.findAll();
    }
}
