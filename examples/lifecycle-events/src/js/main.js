import confetti from 'canvas-confetti';

const button = document.getElementById('confettiButton');

if (button) {
  button.addEventListener('click', () => {
    confetti({
      particleCount: 130,
      spread: 85,
      origin: { y: 0.72 },
    });
  });
}
