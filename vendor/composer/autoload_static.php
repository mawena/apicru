<?php

// autoload_static.php @generated by Composer

namespace Composer\Autoload;

class ComposerStaticInit203acb2d97bb1e3acfafcfa50bcb0c4b
{
    public static $prefixLengthsPsr4 = array (
        'M' => 
        array (
            'Mawena\\Apicru\\' => 14,
        ),
    );

    public static $prefixDirsPsr4 = array (
        'Mawena\\Apicru\\' => 
        array (
            0 => __DIR__ . '/../..' . '/src',
        ),
    );

    public static $classMap = array (
        'Composer\\InstalledVersions' => __DIR__ . '/..' . '/composer/InstalledVersions.php',
    );

    public static function getInitializer(ClassLoader $loader)
    {
        return \Closure::bind(function () use ($loader) {
            $loader->prefixLengthsPsr4 = ComposerStaticInit203acb2d97bb1e3acfafcfa50bcb0c4b::$prefixLengthsPsr4;
            $loader->prefixDirsPsr4 = ComposerStaticInit203acb2d97bb1e3acfafcfa50bcb0c4b::$prefixDirsPsr4;
            $loader->classMap = ComposerStaticInit203acb2d97bb1e3acfafcfa50bcb0c4b::$classMap;

        }, null, ClassLoader::class);
    }
}