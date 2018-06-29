import argparse

from generators.master import MasterGenerator


def generate():
    pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Crossover data generator')

    MasterGenerator.generate()