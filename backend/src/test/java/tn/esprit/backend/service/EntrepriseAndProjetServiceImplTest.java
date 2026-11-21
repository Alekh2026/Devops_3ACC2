package tn.esprit.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Entreprise;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.repository.EntrepriseRepository;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.impl.EntrepriseServiceImpl;
import tn.esprit.backend.service.impl.ProjetServiceImpl;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntrepriseAndProjetServiceImplTest {

    @Mock
    private EntrepriseRepository entrepriseRepository;
    @Mock
    private ProjetRepository projetRepository;

    @InjectMocks
    private EntrepriseServiceImpl entrepriseService;
    @InjectMocks
    private ProjetServiceImpl projetService;

    @Test
    void entrepriseCrudShouldDelegateToRepository() {
        Entreprise entreprise = Entreprise.builder().id(1L).nom("Esprit").adresse("Tunis").build();
        when(entrepriseRepository.save(entreprise)).thenReturn(entreprise);
        when(entrepriseRepository.findById(1L)).thenReturn(Optional.of(entreprise));
        when(entrepriseRepository.findById(2L)).thenReturn(Optional.empty());
        when(entrepriseRepository.findAll()).thenReturn(List.of(entreprise));

        assertSame(entreprise, entrepriseService.addEntreprise(entreprise));
        assertSame(entreprise, entrepriseService.updateEntreprise(entreprise));
        assertSame(entreprise, entrepriseService.getEntrepriseById(1L));
        assertNull(entrepriseService.getEntrepriseById(2L));
        assertEquals(1, entrepriseService.getAllEntreprises().size());
        entrepriseService.deleteEntreprise(1L);

        verify(entrepriseRepository, times(2)).save(entreprise);
        verify(entrepriseRepository).deleteById(1L);
    }

    @Test
    void projetCrudShouldDelegateToRepository() {
        Projet projet = Projet.builder().id(1L).sujet("DevOps").build();
        when(projetRepository.save(projet)).thenReturn(projet);
        when(projetRepository.findById(1L)).thenReturn(Optional.of(projet));
        when(projetRepository.findById(2L)).thenReturn(Optional.empty());
        when(projetRepository.findAll()).thenReturn(List.of(projet));

        assertSame(projet, projetService.addProjet(projet));
        assertSame(projet, projetService.updateProjet(projet));
        assertSame(projet, projetService.getProjetById(1L));
        assertNull(projetService.getProjetById(2L));
        assertEquals(List.of(projet), projetService.getAllProjets());
        projetService.deleteProjet(1L);

        verify(projetRepository, times(2)).save(projet);
        verify(projetRepository).deleteById(1L);
    }
}
