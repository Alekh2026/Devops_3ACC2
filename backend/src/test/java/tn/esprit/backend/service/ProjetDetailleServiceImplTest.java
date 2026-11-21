package tn.esprit.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tn.esprit.backend.entity.Projet;
import tn.esprit.backend.entity.ProjetDetaille;
import tn.esprit.backend.exception.ResourceNotFoundException;
import tn.esprit.backend.repository.ProjetDetailleRepository;
import tn.esprit.backend.repository.ProjetRepository;
import tn.esprit.backend.service.impl.ProjetDetailleServiceImpl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProjetDetailleServiceImplTest {

    @Mock
    private ProjetDetailleRepository projetDetailleRepository;
    @Mock
    private ProjetRepository projetRepository;

    @InjectMocks
    private ProjetDetailleServiceImpl projetDetailleService;

    private ProjetDetaille detail;

    @BeforeEach
    void setUp() {
        detail = ProjetDetaille.builder()
                .id(1L).description("Pipeline").technologie("Jenkins")
                .coutProvisoire(1500.0).dateDebut(LocalDate.of(2026, 1, 1))
                .build();
    }

    @Test
    void addAndUpdateShouldSave() {
        when(projetDetailleRepository.save(detail)).thenReturn(detail);
        assertSame(detail, projetDetailleService.addProjetDetaille(detail));
        assertSame(detail, projetDetailleService.updateProjetDetaille(detail));
        verify(projetDetailleRepository, times(2)).save(detail);
    }

    @Test
    void deleteShouldDelegate() {
        projetDetailleService.deleteProjetDetaille(1L);
        verify(projetDetailleRepository).deleteById(1L);
    }

    @Test
    void getByIdShouldReturnDetailOrNull() {
        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.of(detail));
        when(projetDetailleRepository.findById(9L)).thenReturn(Optional.empty());
        assertSame(detail, projetDetailleService.getProjetDetailleById(1L));
        assertNull(projetDetailleService.getProjetDetailleById(9L));
    }

    @Test
    void listQueriesShouldReturnRepositoryResults() {
        when(projetDetailleRepository.findAll()).thenReturn(List.of(detail));
        when(projetDetailleRepository.findByProjetId(2L)).thenReturn(List.of(detail));
        assertEquals(List.of(detail), projetDetailleService.getAllProjetsDetailles());
        assertEquals(List.of(detail), projetDetailleService.getProjetDetaillesByProjet(2L));
    }

    @Test
    void assignShouldLinkDetailToProjet() {
        Projet projet = Projet.builder().id(2L).sujet("DevOps").build();
        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.of(detail));
        when(projetRepository.findById(2L)).thenReturn(Optional.of(projet));
        when(projetDetailleRepository.save(any(ProjetDetaille.class))).thenAnswer(inv -> inv.getArgument(0));

        assertSame(projet, projetDetailleService.assignProjetDetailleToProjet(1L, 2L).getProjet());
    }

    @Test
    void assignShouldFailWhenDetailOrProjetMissing() {
        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> projetDetailleService.assignProjetDetailleToProjet(1L, 2L));

        when(projetDetailleRepository.findById(1L)).thenReturn(Optional.of(detail));
        when(projetRepository.findById(2L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> projetDetailleService.assignProjetDetailleToProjet(1L, 2L));
        verify(projetDetailleRepository, never()).save(any());
    }
}
