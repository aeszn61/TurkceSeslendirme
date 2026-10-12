"""Türkçe TTS yardımcıları: espeak'siz Türkçe G2P (harf → IPA ses birimi)."""
from .g2p import fonemlere_cevir, fonem_idleri

__all__ = ["fonemlere_cevir", "fonem_idleri"]
